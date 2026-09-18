package com.zhizao.service;

import java.util.List;

/**
 * 物料齐套校验结果。
 */
public record KitCheckResult(boolean passed, List<ShortageItem> shortages) {

    public static KitCheckResult pass() {
        return new KitCheckResult(true, List.of());
    }

    public record ShortageItem(String matCode, String matName, int need, int stock) {
    }
}
