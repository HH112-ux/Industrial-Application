package com.zhizao.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 设备密钥加解密：使用 AES-256-GCM 保护数据库中存储的设备密钥，密钥来自环境变量。
 *
 * <p>数据库中不保存设备密钥明文。HMAC 校验需要还原密钥，因此采用可逆加密而不是哈希；
 * 主密钥缺失时签发与校验都会失败，避免静默降级为明文存储。</p>
 *
 * @author Caspian
 */
@Component
public class DeviceSecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec key;

    /**
     * 使用配置中的 Base64 主密钥初始化。
     *
     * @param base64Key 32 字节主密钥的 Base64 编码，来源于环境变量 `DEVICE_CREDENTIAL_KEY`
     */
    public DeviceSecretCipher(@Value("${device.credential-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            this.key = null;
        } else {
            byte[] raw = Base64.getDecoder().decode(base64Key);
            if (raw.length != 32) {
                throw new IllegalStateException("DEVICE_CREDENTIAL_KEY 必须是 32 字节密钥的 Base64 编码");
            }
            this.key = new SecretKeySpec(raw, "AES");
        }
    }

    /**
     * 加密设备密钥。
     *
     * @param plainSecret 设备密钥明文
     * @return Base64 编码的 `IV + 密文`，可直接写入 `secret_ciphertext`
     * @throws IllegalStateException 主密钥未配置时抛出
     */
    public String encrypt(String plainSecret) {
        requireKey();
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainSecret.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("设备密钥加密失败", e);
        }
    }

    /**
     * 解密设备密钥。
     *
     * @param ciphertext Base64 编码的 `IV + 密文`
     * @return 设备密钥明文
     * @throws IllegalStateException 主密钥未配置或密文无法解密时抛出
     */
    public String decrypt(String ciphertext) {
        requireKey();
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertext);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] decrypted = cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("设备密钥解密失败", e);
        }
    }

    /**
     * 校验主密钥是否已配置。
     *
     * @return 已配置返回 true
     */
    public boolean isConfigured() {
        return key != null;
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException("DEVICE_CREDENTIAL_KEY 未配置，无法处理设备凭证");
        }
    }
}
