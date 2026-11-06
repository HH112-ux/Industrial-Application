package com.zhizao.common;

/**
 * 设备鉴权异常：区分「凭证无效」与「设备已停用」，并携带应返回的 HTTP 状态码。
 *
 * @author Caspian
 */
public class DeviceAuthException extends RuntimeException {

    private final int status;

    /**
     * 创建设备鉴权异常。
     *
     * @param status  应返回给调用方的 HTTP 状态码，401 表示凭证无效，403 表示设备停用
     * @param message 面向调用方的错误说明
     */
    public DeviceAuthException(int status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * 获取应返回的 HTTP 状态码。
     *
     * @return 401 或 403
     */
    public int getStatus() {
        return status;
    }
}
