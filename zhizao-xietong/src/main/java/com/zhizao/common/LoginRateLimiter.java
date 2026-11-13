package com.zhizao.common;

/**
 * 登录限流端口：按调用方给出的键限制滑动窗口内的登录尝试次数。
 *
 * <p>当前实现为单实例本地限流（{@link LocalLoginRateLimiter}），计数保存在进程内存中。
 * <strong>多实例部署必须替换为共享存储实现</strong>（如 Redis），否则各实例独立计数，
 * 实际允许的尝试次数会随实例数放大。替换时只新增实现类，不改调用方。</p>
 *
 * <p>本地登录同时检查两个维度：账号维度用用户名做键，阻止针对单个账号的口令猜测；
 * IP 维度用 {@code login-ip:客户端IP} 做键并使用更宽松的阈值，阻止同一来源
 * 换用户名进行口令喷洒。SSO 登录按 {@code sso:客户端IP} 计数。
 * 客户端 IP 依赖 {@link ClientIpResolver}，部署在反向代理后时必须正确配置
 * {@code TRUSTED_PROXIES}，否则所有请求会共用代理地址的额度。</p>
 *
 * @author Caspian
 */
public interface LoginRateLimiter {

    /**
     * 使用实现默认阈值记录一次登录尝试。
     *
     * @param key 限流键，为空按匿名处理
     * @throws BusinessException 窗口内尝试次数超过阈值时抛出
     */
    void check(String key);

    /**
     * 使用指定阈值记录一次登录尝试。
     *
     * @param key         限流键，为空按匿名处理
     * @param maxAttempts 窗口内允许的最大尝试次数，必须大于 0
     * @throws BusinessException 窗口内尝试次数超过阈值时抛出
     */
    void check(String key, int maxAttempts);
}
