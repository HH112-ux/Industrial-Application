package com.zhizao.ai;

/**
 * 模型调用不可用：超时、网络失败、返回内容无法解析时抛出。
 *
 * <p>异常消息只描述类别，不携带 prompt、响应正文或密钥。</p>
 *
 * @author Caspian
 */
public class AiUnavailableException extends RuntimeException {

    /**
     * 构造不可用异常。
     *
     * @param message 不包含敏感内容的失败类别
     */
    public AiUnavailableException(String message) {
        super(message);
    }
}
