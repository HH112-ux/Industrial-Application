package com.zhizao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 设备凭证：保存设备上报所需的 key_id 与加密后的密钥，一设备一条。
 *
 * @author Caspian
 */
@TableName("eq_device_credential")
public class DeviceCredential {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deviceId;
    private String keyId;
    private String secretCiphertext;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;

    /**
     * 获取主键。
     *
     * @return 凭证主键
     */
    public Long getId() { return id; }

    /**
     * 设置主键。
     *
     * @param id 凭证主键
     */
    public void setId(Long id) { this.id = id; }

    /**
     * 获取所属设备主键。
     *
     * @return 设备主键
     */
    public Long getDeviceId() { return deviceId; }

    /**
     * 设置所属设备主键。
     *
     * @param deviceId 设备主键
     */
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }

    /**
     * 获取凭证标识。
     *
     * @return 凭证标识，随设备请求传递
     */
    public String getKeyId() { return keyId; }

    /**
     * 设置凭证标识。
     *
     * @param keyId 凭证标识
     */
    public void setKeyId(String keyId) { this.keyId = keyId; }

    /**
     * 获取加密后的设备密钥。
     *
     * @return AES-GCM 密文，不返回明文
     */
    public String getSecretCiphertext() { return secretCiphertext; }

    /**
     * 设置加密后的设备密钥。
     *
     * @param secretCiphertext AES-GCM 密文
     */
    public void setSecretCiphertext(String secretCiphertext) { this.secretCiphertext = secretCiphertext; }

    /**
     * 获取启用状态。
     *
     * @return 1 表示启用，0 表示已吊销
     */
    public Integer getEnabled() { return enabled; }

    /**
     * 设置启用状态。
     *
     * @param enabled 1 表示启用，0 表示已吊销
     */
    public void setEnabled(Integer enabled) { this.enabled = enabled; }

    /**
     * 获取签发时间。
     *
     * @return 签发时间
     */
    public LocalDateTime getCreatedAt() { return createdAt; }

    /**
     * 设置签发时间。
     *
     * @param createdAt 签发时间
     */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /**
     * 获取最近一次校验通过时间。
     *
     * @return 最近使用时间，未使用过返回 null
     */
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }

    /**
     * 设置最近一次校验通过时间。
     *
     * @param lastUsedAt 最近使用时间
     */
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}
