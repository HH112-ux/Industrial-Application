package com.zhizao.ai;

/**
 * SQL 未通过只读白名单校验。
 *
 * @author Caspian
 */
public class SqlRejectedException extends RuntimeException {

    /**
     * 构造拒绝异常。
     *
     * @param message 拒绝原因，写入审计的 error_code，不含原始 prompt
     */
    public SqlRejectedException(String message) {
        super(message);
    }
}
