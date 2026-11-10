package com.zhizao.service;

import java.util.List;

/**
 * 物料齐套校验结果。
 *
 * @param passed    是否齐套
 * @param shortages 缺料明细
 * @param error     非缺料类失败原因，例如未配置 BOM；通过或纯缺料时为 null
 */
public record KitCheckResult(boolean passed, List<ShortageItem> shortages, String error) {

    public static KitCheckResult pass() {
        return new KitCheckResult(true, List.of(), null);
    }

    /**
     * 未配置 BOM：无法判定齐套，按失败处理，避免被误判为齐套通过。
     *
     * @return 未配置 BOM 的失败结果
     */
    public static KitCheckResult missingBom() {
        return new KitCheckResult(false, List.of(), "工单未配置 BOM，无法判定齐套");
    }

    /**
     * 缺料明细：需求数量与扣减其他工单预占后的可用数量。
     *
     * @param matCode   物料编码
     * @param matName   物料名称
     * @param need      需求数量
     * @param available 可用数量（库存 − 其他工单已预占）
     */
    public record ShortageItem(String matCode, String matName, int need, int available) {
    }
}
