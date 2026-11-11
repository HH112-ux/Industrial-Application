package com.zhizao.dto;

/**
 * 取消入库请求。
 *
 * <p>操作人不再由请求体传入，统一取登录令牌，避免伪造操作人。</p>
 *
 * @param reason 取消原因，必填，最长 255 字
 * @author Caspian
 */
public record InboundCancelRequest(String reason) {
}
