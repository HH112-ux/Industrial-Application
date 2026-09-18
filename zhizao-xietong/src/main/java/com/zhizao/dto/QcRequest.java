package com.zhizao.dto;

/**
 * 质检请求。result 取值 PASS / FAIL。
 */
public record QcRequest(String result, String defectDesc, String inspector) {
}
