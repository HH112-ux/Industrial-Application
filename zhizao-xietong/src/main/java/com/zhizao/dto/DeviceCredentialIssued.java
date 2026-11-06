package com.zhizao.dto;

/**
 * 设备凭证签发结果：明文密钥只在签发响应中出现一次，服务端只保存密文。
 *
 * @author Caspian
 */
public record DeviceCredentialIssued(String deviceNo, String keyId, String secret) {
}
