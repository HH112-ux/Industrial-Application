package com.zhizao.common;

/**
 * 访问被拒绝：用于账号禁用、跨车间越权等场景，统一返回 HTTP 403。
 *
 * @author Caspian
 */
public class ForbiddenException extends RuntimeException {

    /**
     * 构造访问拒绝异常。
     *
     * @param message 可读的拒绝原因
     */
    public ForbiddenException(String message) {
        super(message);
    }
}
