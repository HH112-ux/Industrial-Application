package com.zhizao.common;

/**
 * 业务异常：请求格式正确但业务规则拒绝，例如状态机守卫、齐套校验不通过。
 *
 * <p>默认业务码为 500，保持既有接口的响应语义不变；新接口可传入明确业务码，
 * 例如并发冲突使用 409，按接口契约以 HTTP 200 + 业务码返回。</p>
 *
 * @author Caspian
 */
public class BusinessException extends RuntimeException {

    private final int code;

    /**
     * 以默认业务码 500 构造异常，用于既有接口。
     *
     * @param message 面向调用方的错误说明
     */
    public BusinessException(String message) {
        this(500, message);
    }

    /**
     * 以指定业务码构造异常，用于新接口区分冲突、重复提交等业务拒绝。
     *
     * @param code    业务码，会写入响应体 `code`
     * @param message 面向调用方的错误说明
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 获取业务码。
     *
     * @return 写入响应体 `code` 的业务码
     */
    public int getCode() {
        return code;
    }
}
