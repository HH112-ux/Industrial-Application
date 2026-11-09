package com.zhizao.dto;

/**
 * 质检请求。result 取值 PASS / FAIL。
 *
 * <p>质检员不再由请求体传入，统一取登录令牌，避免伪造质检人。</p>
 *
 * @param result     质检结论 PASS / FAIL
 * @param defectDesc 不良描述，可为空
 * @author Caspian
 */
public record QcRequest(String result, String defectDesc) {
}
