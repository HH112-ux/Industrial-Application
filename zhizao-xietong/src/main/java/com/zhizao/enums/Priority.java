package com.zhizao.enums;

/**
 * 生产优先级。
 */
public enum Priority {

    HIGH(3, "高"),
    MEDIUM(2, "中"),
    LOW(1, "低");

    private final int weight;
    private final String label;

    Priority(int weight, String label) {
        this.weight = weight;
        this.label = label;
    }

    public int getWeight() {
        return weight;
    }

    public String getLabel() {
        return label;
    }
}
