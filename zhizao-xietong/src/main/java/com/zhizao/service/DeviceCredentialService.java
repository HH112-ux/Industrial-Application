package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.common.BusinessException;
import com.zhizao.dto.DeviceCredentialIssued;
import com.zhizao.entity.Device;
import com.zhizao.entity.DeviceCredential;
import com.zhizao.mapper.DeviceCredentialMapper;
import com.zhizao.mapper.DeviceMapper;
import com.zhizao.security.DeviceSecretCipher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 设备凭证服务：签发、轮换与吊销设备凭证，明文密钥只在签发时返回一次。
 *
 * @author Caspian
 */
@Service
public class DeviceCredentialService {

    private static final int SECRET_BYTES = 32;
    private static final int KEY_ID_BYTES = 8;

    private final DeviceMapper deviceMapper;
    private final DeviceCredentialMapper credentialMapper;
    private final DeviceSecretCipher secretCipher;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 构造设备凭证服务。
     *
     * @param deviceMapper     设备数据访问
     * @param credentialMapper 设备凭证数据访问
     * @param secretCipher     设备密钥加解密组件
     */
    public DeviceCredentialService(DeviceMapper deviceMapper, DeviceCredentialMapper credentialMapper,
                                   DeviceSecretCipher secretCipher) {
        this.deviceMapper = deviceMapper;
        this.credentialMapper = credentialMapper;
        this.secretCipher = secretCipher;
    }

    /**
     * 为设备签发凭证；已有凭证时轮换 key_id 与密钥。
     *
     * @param deviceId 设备主键
     * @return 本次签发结果，包含仅返回一次的明文密钥
     * @throws BusinessException 设备不存在或主密钥未配置时抛出
     */
    @Transactional
    public DeviceCredentialIssued issue(Long deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException("设备不存在");
        }
        if (!secretCipher.isConfigured()) {
            throw new BusinessException("设备凭证密钥未配置，请设置环境变量 DEVICE_CREDENTIAL_KEY");
        }
        String secret = Base64.getEncoder().encodeToString(randomBytes(SECRET_BYTES));
        String keyId = "DK-" + HexFormat.of().formatHex(randomBytes(KEY_ID_BYTES));
        DeviceCredential credential = findByDeviceId(deviceId);
        if (credential == null) {
            credential = new DeviceCredential();
            credential.setDeviceId(deviceId);
            credential.setCreatedAt(LocalDateTime.now());
            applyCredential(credential, keyId, secret);
            credentialMapper.insert(credential);
        } else {
            applyCredential(credential, keyId, secret);
            credentialMapper.updateById(credential);
        }
        return new DeviceCredentialIssued(device.getDeviceNo(), keyId, secret);
    }

    /**
     * 吊销设备凭证，已签发的 key_id 立即失效。
     *
     * @param deviceId 设备主键
     * @throws BusinessException 设备凭证不存在时抛出
     */
    @Transactional
    public void revoke(Long deviceId) {
        DeviceCredential credential = findByDeviceId(deviceId);
        if (credential == null) {
            throw new BusinessException("设备凭证不存在");
        }
        credential.setEnabled(0);
        credentialMapper.updateById(credential);
    }

    private void applyCredential(DeviceCredential credential, String keyId, String secret) {
        credential.setKeyId(keyId);
        credential.setSecretCiphertext(secretCipher.encrypt(secret));
        credential.setEnabled(1);
    }

    private DeviceCredential findByDeviceId(Long deviceId) {
        return credentialMapper.selectOne(
                new LambdaQueryWrapper<DeviceCredential>().eq(DeviceCredential::getDeviceId, deviceId));
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }
}
