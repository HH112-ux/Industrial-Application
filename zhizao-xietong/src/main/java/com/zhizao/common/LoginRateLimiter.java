package com.zhizao.common;

/**
 * 登录限流端口：按「用户名 + 客户端 IP」限制滑动窗口内的登录尝试次数。
 *
 * <p>当前实现为单实例本地限流（{@link LocalLoginRateLimiter}），计数保存在进程内存中。
 * <strong>多实例部署必须替换为共享存储实现</strong>（如 Redis），否则各实例独立计数，
 * 实际允许的尝试次数会随实例数放大。替换时只新增实现类，不改调用方。</p>
 *
 * <p>限流键由调用方构造：本地登录为 {@code 用户名|客户端IP}，SSO 为 {@code sso:客户端IP}。
 * 客户端 IP 依赖 {@link ClientIpResolver}，部署在反向代理后时必须正确配置
 * {@code TRUSTED_PROXIES}，否则所有请求会共用代理地址的额度。</p>
 *
 * @author Caspian
 */
public interface LoginRateLimiter {

    /**
     * 记录一次登录尝试；超过阈值时拒绝。
     *
     * @param key 限流键，由调用方按「用户名 + 客户端 IP」构造；为空按匿名处理
     * @throws BusinessException 窗口内尝试次数超过阈值时抛出
     */
    void check(String key);
}
