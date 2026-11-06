package com.zhizao.dto;

/**
 * 管理员分配用户车间数据域请求。
 *
 * @param workshopScope 车间名称，不能为空
 * @author Caspian
 */
public record WorkshopAssignRequest(String workshopScope) {
}
