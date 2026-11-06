package com.zhizao.dto.ai;

/**
 * AI 旁路调用结果：模型不可用时返回 {@code available=false} 与原因，而不是抛错或返回空数组。
 *
 * @param available 是否产出可用建议
 * @param reason    不可用原因；可用时为 null
 * @param data      建议内容；不可用时为 null
 * @param <T>       建议类型
 * @author Caspian
 */
public record AiResult<T>(boolean available, String reason, T data) {

    /**
     * 构造可用结果。
     *
     * @param data 建议内容
     * @param <T>  建议类型
     * @return 可用结果
     */
    public static <T> AiResult<T> available(T data) {
        return new AiResult<>(true, null, data);
    }

    /**
     * 构造降级结果。
     *
     * @param reason 面向调用方的降级原因
     * @param <T>    建议类型
     * @return 不可用结果
     */
    public static <T> AiResult<T> unavailable(String reason) {
        return new AiResult<>(false, reason, null);
    }
}
