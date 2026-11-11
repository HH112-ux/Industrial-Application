package com.zhizao.dto;

/**
 * 企微扫码登录请求。
 *
 * @param code  企微回调携带的一次性授权码
 * @param state 本服务签发的一次性 state，与浏览器 Cookie 绑定
 */
public record SsoLoginRequest(String code, String state) {
}
