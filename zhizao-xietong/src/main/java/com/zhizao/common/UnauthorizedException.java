package com.zhizao.common;

/**
 * 未认证或令牌已失效：统一返回 HTTP 401，前端据此清理本地令牌并跳转登录。
 *
 * @author Caspian
 */
public class UnauthorizedException extends RuntimeException {

    /**
     * 构造未认证异常。
     *
     * @param message 可读的失效原因
     */
    public UnauthorizedException(String message) {
        super(message);
    }
}
