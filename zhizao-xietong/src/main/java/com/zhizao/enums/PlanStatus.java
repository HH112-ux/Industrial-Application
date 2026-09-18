package com.zhizao.enums;

/**
 * 生产计划状态。
 */
public enum PlanStatus {

    PENDING_SCHEDULE("待排产"),
    IN_PROGRESS("进行中"),
    FINISHED("已完成");

    private final String label;

    PlanStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
