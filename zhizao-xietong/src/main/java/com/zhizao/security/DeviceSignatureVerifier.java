package com.zhizao.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.common.DeviceAuthException;
import com.zhizao.dto.DeviceGatewayReport;
import com.zhizao.entity.DeviceCredential;
import com.zhizao.mapper.DeviceCredentialMapper;
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
 * 设备上报签名校验：校验 key_id 对应的凭证、HMAC-SHA256 签名与时间窗口。
 *
 * <p>REST 上报对原始请求体签名；MQTT 上报对规范化字段串签名，规范串格式为
 * {@code deviceNo|status|temperature|rpm|current|timestamp|nonce}，数值使用去尾零的十进制表示。</p>
 *
 * @author Caspian
 */
@Component
public class DeviceSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(DeviceSignatureVerifier.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final DeviceCredentialMapper credentialMapper;
    private final DeviceSecretCipher secretCipher;

    @Value("${device.auth.enabled:false}")
    private boolean enabled;
    @Value("${device.signature-tolerance-seconds:300}")
    private long toleranceSeconds;

    /**
     * 构造签名校验器。
     *
     * @param credentialMapper 设备凭证访问
     * @param secretCipher     设备密钥加解密组件
     */
    public DeviceSignatureVerifier(DeviceCredentialMapper credentialMapper, DeviceSecretCipher secretCipher) {
        this.credentialMapper = credentialMapper;
        this.secretCipher = secretCipher;
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
     * 校验 REST 上报签名，签名对象为原始请求体。
     *
     * @param keyId       请求头 `X-Device-Key` 中的凭证标识
     * @param signature   请求头 `X-Device-Signature` 中的十六进制 HMAC-SHA256
     * @param rawPayload  原始请求体字符串
     * @throws DeviceAuthException 凭证缺失或无效返回 401，设备已停用返回 403
     */
    public void verifyRawPayload(String keyId, String signature, String rawPayload) {
        if (!enabled) {
            return;
        }
        DeviceCredential credential = requireEnabledCredential(keyId);
        String secret = decryptSecret(credential);
        String expected = hmacHex(secret, rawPayload);
        if (!constantTimeEquals(expected, signature)) {
            throw new DeviceAuthException(401, "设备签名校验失败");
        }
        touch(credential);
    }

    /**
     * 校验 MQTT 上报签名，签名对象为规范化字段串，并校验时间戳是否在容忍窗口内。
     *
     * @param report 设备上报内容，需包含 keyId、timestamp、nonce 与 signature
     * @throws DeviceAuthException 凭证缺失、签名错误或时间戳越界返回 401，设备已停用返回 403
     */
    public void verifyReport(DeviceGatewayReport report) {
        if (!enabled) {
            return;
        }
        if (report.timestamp() == null) {
            throw new DeviceAuthException(401, "设备上报缺少时间戳");
        }
        long skewSeconds = Math.abs(Instant.now().toEpochMilli() - report.timestamp()) / 1000;
        if (skewSeconds > toleranceSeconds) {
            throw new DeviceAuthException(401, "设备上报时间戳超出允许窗口");
        }
        DeviceCredential credential = requireEnabledCredential(report.keyId());
        String secret = decryptSecret(credential);
        String expected = hmacHex(secret, canonicalReport(report));
        if (!constantTimeEquals(expected, report.signature())) {
            throw new DeviceAuthException(401, "设备签名校验失败");
        }
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
            throw new DeviceAuthException(401, "缺少设备凭证标识");
        }
        DeviceCredential credential = credentialMapper.selectOne(
                new LambdaQueryWrapper<DeviceCredential>().eq(DeviceCredential::getKeyId, keyId));
        if (credential == null) {
            throw new DeviceAuthException(401, "设备凭证无效");
        }
        if (!Integer.valueOf(1).equals(credential.getEnabled())) {
            throw new DeviceAuthException(403, "设备已停用");
        }
        return credential;
    }

    private String decryptSecret(DeviceCredential credential) {
        try {
            return secretCipher.decrypt(credential.getSecretCiphertext());
        } catch (IllegalStateException e) {
            log.error("设备凭证解密失败，keyId={}", credential.getKeyId(), e);
            throw new DeviceAuthException(401, "设备凭证不可用");
        }
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
