package com.zhizao.common;

/**
 * 请求参数非法异常：由全局处理器转换为 HTTP 400，避免与业务拒绝混用。
 *
 * @author Caspian
 */
public class BadRequestException extends RuntimeException {

    /**
     * 构造参数非法异常。
     *
     * @param message 面向调用方的错误说明
     */
    public BadRequestException(String message) {
        super(message);
    }
}
