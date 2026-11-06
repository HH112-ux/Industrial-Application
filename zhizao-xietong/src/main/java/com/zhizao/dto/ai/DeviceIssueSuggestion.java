package com.zhizao.dto.ai;

import java.util.List;

/**
 * 设备异常归因建议。每条原因必须携带证据，否则视为无效输出。
 *
 * @param causes          可能原因与对应证据
 * @param actions         建议动作，只是文本，不创建维保记录
 * @param maintenanceRefs 引用的维修记录主键
 * @param confidence      置信度 0-1
 * @param disclaimer      免责声明，提示人工确认
 * @author Caspian
 */
public record DeviceIssueSuggestion(List<Cause> causes,
                                    List<String> actions,
                                    List<Long> maintenanceRefs,
                                    double confidence,
                                    String disclaimer) {

    /**
     * 单条原因及其证据。
     *
     * @param cause    原因描述
     * @param evidence 引用自输入数据或指标的证据
     */
    public record Cause(String cause, String evidence) {
    }
}
