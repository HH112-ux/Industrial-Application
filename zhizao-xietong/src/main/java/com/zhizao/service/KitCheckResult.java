package com.zhizao.service;

import java.util.List;

/**
 * 物料齐套校验结果。
 *
 * @param passed    是否齐套
 * @param shortages 缺料明细
 */
public record KitCheckResult(boolean passed, List<ShortageItem> shortages) {

    public static KitCheckResult pass() {
        return new KitCheckResult(true, List.of());
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
