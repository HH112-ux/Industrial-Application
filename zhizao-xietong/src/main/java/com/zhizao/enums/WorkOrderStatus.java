package com.zhizao.enums;

/**
 * 生产工单状态。
 */
public enum WorkOrderStatus {

    PENDING("待开工"),
    IN_PRODUCTION("生产中"),
    PENDING_QC("待质检"),
    FINISHED("已完工");

    private final String label;

    WorkOrderStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
