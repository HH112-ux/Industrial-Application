package com.zhizao.dto;

/**
 * 企业统一身份（SSO）可用状态，供登录页决定是否展示扫码入口。
 *
 * @param enabled 灰度开关是否开启
 * @author Caspian
 */
public record SsoStatus(boolean enabled) {
}
