package com.zhizao.enums;

/**
 * 排程结果状态。
 *
 * @author Caspian
 */
public enum ScheduleStatus {

    SCHEDULED("已排入"),
    UNSCHEDULED("无法排入");

    private final String label;

    ScheduleStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
