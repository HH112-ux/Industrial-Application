package com.zhizao.common;

/**
 * AI 能力未启用：由全局处理器转成 HTTP 503 与业务码 503，
 * 与未预期异常（500）和空结果区分开。
 *
 * @author Caspian
 */
public class AiDisabledException extends RuntimeException {

    /**
     * 构造未启用异常。
     *
     * @param message 面向调用方的说明
     */
    public AiDisabledException(String message) {
        super(message);
    }
}
