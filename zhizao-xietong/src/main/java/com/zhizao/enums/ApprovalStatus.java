package com.zhizao.enums;

/**
 * 工艺审批状态。
 */
public enum ApprovalStatus {

    DRAFT("草稿"),
    PENDING("审批中"),
    PUBLISHED("已发布"),
    REJECTED("已驳回");

    private final String label;

    ApprovalStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
