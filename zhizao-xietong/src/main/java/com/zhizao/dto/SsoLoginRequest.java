package com.zhizao.dto;

/**
 * 企微扫码登录请求。
 *
 * @param code 企微回调携带的一次性授权码
 */
public record SsoLoginRequest(String code) {
}
