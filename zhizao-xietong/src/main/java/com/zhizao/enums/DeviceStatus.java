package com.zhizao.enums;

/**
 * 设备状态。
 */
public enum DeviceStatus {

    STOPPED("停机"),
    RUNNING("运行"),
    MAINTENANCE("维修"),
    SCRAPPED("报废");

    private final String label;

    DeviceStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
