package com.zhizao.common;

/**
 * 业务异常：用于状态机守卫、齐套校验等业务规则拒绝。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
