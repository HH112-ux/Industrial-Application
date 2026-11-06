package com.zhizao.enums;

/**
 * 排程冲突业务码：无法排入时返回给调用方的稳定标识，配合 {@code message} 说明原因。
 *
 * <p>不静默挪期、不悄悄拉长工期：所有落不下的情况都落到这里的一个码。</p>
 *
 * @author Caspian
 */
public enum ScheduleConflict {

    PLAN_MISSING("计划不存在，无法读取交期与优先级"),
    PROCESS_MISSING("工单未绑定工艺，无法取得工序路线"),
    OPERATION_MISSING("工艺没有可排产工序"),
    DEVICE_TYPE_MISSING("工序未配置设备类型，且计划未指定可用设备"),
    NO_CAPACITY("设备类型「%s」没有运行中的设备"),
    DURATION_INVALID("工单数量无效，无法计算工序工时"),
    STANDARD_HOURS_MISSING("工序「%s」缺少标准工时，无法排程；请补充工时或显式开启估算配置"),
    DUE_DATE_MISSING("计划缺少开始或完成日期，无法确定排程窗口"),
    DUE_DATE_MISSED("上一工序结束时间 %s 已晚于计划交期 %s"),
    CAPACITY_OVERLOAD("设备类型「%s」在 %s 至 %s 的产能已被占满，工序无法在交期内排入");

    private final String template;

    ScheduleConflict(String template) {
        this.template = template;
    }

    /**
     * 获取业务码名称，写入排程结果的 {@code conflict_code}。
     *
     * @return 枚举名称
     */
    public String code() {
        return name();
    }

    /**
     * 渲染可读消息。
     *
     * @param args 模板参数
     * @return 面向调用方的原因说明
     */
    public String message(Object... args) {
        return String.format(template, args);
    }
}
