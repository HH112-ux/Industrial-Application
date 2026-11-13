package com.zhizao.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.common.DeviceAuthException;
import com.zhizao.dto.DeviceGatewayReport;
import com.zhizao.entity.Device;
import com.zhizao.entity.DeviceCredential;
import com.zhizao.mapper.DeviceCredentialMapper;
import com.zhizao.mapper.DeviceMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 设备上报签名校验：校验 key_id 对应的凭证、HMAC-SHA256 签名、凭证与设备编号的绑定关系与时间窗口。
 *
 * <p>REST 上报对原始请求体签名；MQTT 上报对规范化字段串签名，规范串格式为
 * {@code deviceNo|status|temperature|rpm|current|timestamp|nonce}，数值使用去尾零的十进制表示。</p>
 *
 * <p>凭证与设备绑定：凭证签发时即通过 {@code eq_device_credential.device_id} 绑定到唯一设备，
 * 校验时按该 device_id 反查 {@code eq_device.device_no} 与报文中的设备编号比对，
 * 不一致或绑定设备已删除时返回 403。因此存量凭证无需重签，设备编号变更也随台账自动生效。</p>
 *
 * @author Caspian
 */
@Component
public class DeviceSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(DeviceSignatureVerifier.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** 日志中外部可控字段的最大长度，避免超长 keyId 刷日志。 */
    private static final int MAX_LOG_VALUE_LENGTH = 64;

    private final DeviceCredentialMapper credentialMapper;
    private final DeviceMapper deviceMapper;
    private final DeviceSecretCipher secretCipher;

    @Value("${device.auth.enabled:false}")
    private boolean enabled;
    @Value("${device.signature-tolerance-seconds:300}")
    private long toleranceSeconds;

    /**
     * 构造签名校验器。
     *
     * @param credentialMapper 设备凭证访问
     * @param deviceMapper     设备台账访问，用于校验凭证与设备编号的绑定关系
     * @param secretCipher     设备密钥加解密组件
     */
    public DeviceSignatureVerifier(DeviceCredentialMapper credentialMapper, DeviceMapper deviceMapper,
                                   DeviceSecretCipher secretCipher) {
        this.credentialMapper = credentialMapper;
        this.deviceMapper = deviceMapper;
        this.secretCipher = secretCipher;
    }

    /**
     * 启动期校验：开启鉴权但未配置主密钥时直接失败，避免重启后整批设备被静默拒绝。
     *
     * @throws IllegalStateException 鉴权开启且 {@code DEVICE_CREDENTIAL_KEY} 未配置时抛出
     */
    @PostConstruct
    void validateConfiguration() {
        if (!enabled) {
            return;
        }
        if (!secretCipher.isConfigured()) {
            throw new IllegalStateException(
                    "device.auth.enabled=true 时必须配置 DEVICE_CREDENTIAL_KEY（32 字节 Base64）");
        }
        log.info("设备 HMAC 鉴权已启用，签名时间窗口 {} 秒", toleranceSeconds);
    }

    /**
     * 判断是否开启设备上报签名校验。
     *
     * @return 开启返回 true；关闭时调用方跳过校验，兼容现有未签名上报
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 校验 REST 上报签名，签名对象为原始请求体，并校验凭证与报文设备编号的绑定关系。
     *
     * @param keyId       请求头 `X-Device-Key` 中的凭证标识
     * @param signature   请求头 `X-Device-Signature` 中的十六进制 HMAC-SHA256
     * @param rawPayload  原始请求体字符串
     * @param deviceNo    请求体中的设备编号，用于与凭证绑定设备比对
     * @throws DeviceAuthException 凭证缺失或无效返回 401，设备已停用、绑定设备已删除或编号不匹配返回 403
     */
    public void verifyRawPayload(String keyId, String signature, String rawPayload, String deviceNo) {
        if (!enabled) {
            return;
        }
        DeviceCredential credential = requireEnabledCredential(keyId);
        String secret = decryptSecret(credential);
        String expected = hmacHex(secret, rawPayload);
        if (!constantTimeEquals(expected, signature)) {
            throw reject(401, "设备签名校验失败", keyId);
        }
        requireDeviceMatch(credential, deviceNo);
        touch(credential);
    }

    /**
     * 校验 MQTT 上报签名，签名对象为规范化字段串，并校验时间戳窗口与凭证设备绑定关系。
     *
     * @param report 设备上报内容，需包含 keyId、timestamp、nonce 与 signature
     * @throws DeviceAuthException 凭证缺失、签名错误或时间戳越界返回 401，设备已停用、绑定设备已删除或编号不匹配返回 403
     */
    public void verifyReport(DeviceGatewayReport report) {
        if (!enabled) {
            return;
        }
        if (report.timestamp() == null) {
            throw reject(401, "设备上报缺少时间戳", report.keyId());
        }
        long skewSeconds = Math.abs(Instant.now().toEpochMilli() - report.timestamp()) / 1000;
        if (skewSeconds > toleranceSeconds) {
            throw reject(401, "设备上报时间戳超出允许窗口", report.keyId());
        }
        DeviceCredential credential = requireEnabledCredential(report.keyId());
        String secret = decryptSecret(credential);
        String expected = hmacHex(secret, canonicalReport(report));
        if (!constantTimeEquals(expected, report.signature())) {
            throw reject(401, "设备签名校验失败", report.keyId());
        }
        requireDeviceMatch(credential, report.deviceNo());
        touch(credential);
    }

    /**
     * 生成 MQTT 上报的签名规范串，设备侧需按同一规则拼接后再计算 HMAC。
     *
     * @param report 设备上报内容
     * @return 以 `|` 分隔的规范串，空值用空字符串占位
     */
    public static String canonicalReport(DeviceGatewayReport report) {
        return String.join("|",
                valueOrEmpty(report.deviceNo()),
                valueOrEmpty(report.status()),
                number(report.temperature()),
                number(report.rpm()),
                number(report.current()),
                Objects.toString(report.timestamp(), ""),
                valueOrEmpty(report.nonce()));
    }

    /**
     * 计算 HMAC-SHA256 并转为小写十六进制。
     *
     * @param secret  设备密钥明文
     * @param payload 待签名内容
     * @return 64 位十六进制签名
     */
    public static String hmacHex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    private DeviceCredential requireEnabledCredential(String keyId) {
        if (!StringUtils.hasText(keyId)) {
            throw reject(401, "缺少设备凭证标识", keyId);
        }
        DeviceCredential credential = credentialMapper.selectOne(
                new LambdaQueryWrapper<DeviceCredential>().eq(DeviceCredential::getKeyId, keyId));
        if (credential == null) {
            throw reject(401, "设备凭证无效", keyId);
        }
        if (!Integer.valueOf(1).equals(credential.getEnabled())) {
            throw reject(403, "设备已停用", keyId);
        }
        return credential;
    }

    /**
     * 校验凭证绑定的设备与报文中的设备编号一致，阻止持有其他设备凭证的伪造上报。
     *
     * <p>拒绝日志保留 keyId、凭证绑定设备编号与报文设备编号，便于事后取证；
     * 返回给调用方的错误信息不包含凭证绑定的设备编号。绑定设备已删除（{@code deleted=1}）
     * 视为不可用，直接拒绝。</p>
     *
     * @param credential 已通过启用状态校验的凭证
     * @param deviceNo   报文中的设备编号
     * @throws DeviceAuthException 绑定设备不存在或编号不一致返回 403
     */
    private void requireDeviceMatch(DeviceCredential credential, String deviceNo) {
        Device boundDevice = credential.getDeviceId() == null ? null : deviceMapper.selectById(credential.getDeviceId());
        if (boundDevice == null) {
            log.warn("设备鉴权拒绝：status=403，keyId={}，原因=凭证绑定的设备不存在或已删除，deviceId={}",
                    sanitizeForLog(credential.getKeyId()), credential.getDeviceId());
            throw new DeviceAuthException(403, "凭证绑定的设备不存在");
        }
        if (!StringUtils.hasText(deviceNo) || !deviceNo.equals(boundDevice.getDeviceNo())) {
            log.warn("设备鉴权拒绝：status=403，keyId={}，原因=凭证与设备编号不匹配，绑定设备={}，上报设备={}",
                    sanitizeForLog(credential.getKeyId()), sanitizeForLog(boundDevice.getDeviceNo()),
                    sanitizeForLog(deviceNo));
            throw new DeviceAuthException(403, "凭证与设备编号不匹配");
        }
    }

    private String decryptSecret(DeviceCredential credential) {
        try {
            return secretCipher.decrypt(credential.getSecretCiphertext());
        } catch (IllegalStateException e) {
            log.error("设备凭证解密失败，keyId={}", sanitizeForLog(credential.getKeyId()), e);
            throw new DeviceAuthException(401, "设备凭证不可用");
        }
    }

    /**
     * 记录鉴权拒绝痕迹并构造异常；只记录 keyId 与原因，不记录密钥或原始报文。
     *
     * @param status 应返回的 HTTP 状态码
     * @param reason 拒绝原因
     * @param keyId  设备凭证标识，可为空
     * @return 带状态码的设备鉴权异常
     */
    private DeviceAuthException reject(int status, String reason, String keyId) {
        log.warn("设备鉴权拒绝：status={}，keyId={}，原因={}", status, sanitizeForLog(keyId), reason);
        return new DeviceAuthException(status, reason);
    }

    /**
     * 清洗外部可控的 keyId 后再写日志：控制字符替换为 `?`，超长部分截断。
     *
     * <p>keyId 可来自请求头或 MQTT 报文，原样落日志可被换行符伪造日志行，
     * 也可被超长内容放大日志体积。</p>
     *
     * @param value 外部输入，可为空
     * @return 可直接写入日志的字符串
     */
    static String sanitizeForLog(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sanitized = new StringBuilder(Math.min(value.length(), MAX_LOG_VALUE_LENGTH));
        for (int i = 0; i < value.length() && sanitized.length() < MAX_LOG_VALUE_LENGTH; i++) {
            char c = value.charAt(i);
            sanitized.append(Character.isISOControl(c) ? '?' : c);
        }
        return sanitized.toString();
    }

    private void touch(DeviceCredential credential) {
        credential.setLastUsedAt(java.time.LocalDateTime.now());
        credentialMapper.updateById(credential);
    }

    private boolean constantTimeEquals(String expected, String provided) {
        if (!StringUtils.hasText(provided)) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String number(Number value) {
        if (value == null) {
            return "";
        }
        return BigDecimal.valueOf(value.doubleValue()).stripTrailingZeros().toPlainString();
    }
}
