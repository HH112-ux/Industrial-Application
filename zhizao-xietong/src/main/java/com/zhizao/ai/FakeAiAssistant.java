package com.zhizao.ai;

import com.zhizao.dto.DeviceTelemetry;
import com.zhizao.dto.ai.AiQueryContext;
import com.zhizao.dto.ai.DeviceIssueContext;
import com.zhizao.dto.ai.DeviceIssueSuggestion;
import com.zhizao.dto.ai.ReportDraft;
import com.zhizao.dto.ai.ReportDraftContext;
import com.zhizao.entity.Device;
import com.zhizao.entity.MaintenanceRecord;
import com.zhizao.entity.QualityRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 确定性的离线假模型：不访问网络、不需要密钥，用于 CI 与本地演示。
 *
 * <p>它按规则从只读上下文中归纳证据、抽取数量、映射预置只读视图。因输出完全可复现，
 * 所有 AI 用例都可以在无外网环境中断言。</p>
 *
 * @author Caspian
 */
@Component
@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "fake", matchIfMissing = true)
public class FakeAiAssistant implements AiAssistant {

    private static final Pattern QTY = Pattern.compile("(\\d+)\\s*件");
    private static final Pattern DONE = Pattern.compile("(?:加工了|生产了|完成)\\s*(\\d+)");
    private static final Pattern QUALIFIED = Pattern.compile("合格\\s*(\\d+)");
    private static final Pattern DEFECT = Pattern.compile("不良\\s*(\\d+)");
    private static final Pattern OPERATOR = Pattern.compile("操作工\\s*[：:]\\s*([^\\s，,。]+)");

    @Override
    public DeviceIssueSuggestion suggestDeviceIssue(DeviceIssueContext context) {
        Device device = context.device();
        List<DeviceIssueSuggestion.Cause> causes = new ArrayList<>();
        if (!"RUNNING".equals(device.getStatus())) {
            causes.add(new DeviceIssueSuggestion.Cause("设备当前非运行状态",
                    "设备状态=" + device.getStatus() + "，设备编号 " + device.getDeviceNo()));
        }
        DeviceTelemetry telemetry = context.telemetry();
        if (telemetry != null && telemetry.temperature() > 60) {
            causes.add(new DeviceIssueSuggestion.Cause("温度偏高，可能存在散热或负载问题",
                    "遥测温度=" + telemetry.temperature() + "℃，采集时间 " + telemetry.timestamp()));
        }
        if (telemetry != null && telemetry.current() > 10) {
            causes.add(new DeviceIssueSuggestion.Cause("电流偏高，可能存在电气负载异常",
                    "遥测电流=" + telemetry.current() + "A，采集时间 " + telemetry.timestamp()));
        }
        if (!context.maintenance().isEmpty()) {
            List<Long> ids = context.maintenance().stream().map(MaintenanceRecord::getId).toList();
            causes.add(new DeviceIssueSuggestion.Cause("近期存在维修或点检记录",
                    "维修记录 " + ids + "，共 " + ids.size() + " 条"));
        }
        if (!context.qualityFail().isEmpty()) {
            List<Long> ids = context.qualityFail().stream().map(QualityRecord::getId).toList();
            causes.add(new DeviceIssueSuggestion.Cause("近期存在质检不合格记录",
                    "质检记录 " + ids + "，共 " + ids.size() + " 条"));
        }

        if (causes.isEmpty()) {
            return new DeviceIssueSuggestion(List.of(), List.of("证据不足，建议人工巡检确认"),
                    List.of(), 0.3, "证据不足，建议人工判断");
        }

        Set<String> actions = new LinkedHashSet<>();
        for (DeviceIssueSuggestion.Cause cause : causes) {
            if (cause.cause().contains("温度")) {
                actions.add("检查温控与冷却系统");
            } else if (cause.cause().contains("电流")) {
                actions.add("检查电气负载与线路");
            } else if (cause.cause().contains("维修")) {
                actions.add("核对近期维修记录与更换件");
            } else if (cause.cause().contains("质检")) {
                actions.add("复核相关工单的质检不良项");
            } else {
                actions.add("确认设备当前状态与现场情况");
            }
        }
        actions.add("以上为建议文本，需人工确认后再处理");
        double confidence = Math.min(0.9, 0.4 + 0.1 * causes.size());
        List<Long> refs = context.maintenance().stream().map(MaintenanceRecord::getId).toList();
        return new DeviceIssueSuggestion(causes, List.copyOf(actions), refs, confidence,
                "以上为系统建议，不改变设备状态，需人工确认");
    }

    @Override
    public ReportDraft draftReport(ReportDraftContext context) {
        String text = StringUtils.hasText(context.text()) ? context.text().trim() : "";
        Integer qty = firstNumber(QTY, text);
        if (qty == null) {
            qty = firstNumber(DONE, text);
        }
        Integer qualified = firstNumber(QUALIFIED, text);
        Integer defect = firstNumber(DEFECT, text);

        List<String> missing = new ArrayList<>();
        if (qty == null) {
            missing.add("qty");
        }
        if (qualified == null) {
            missing.add("qualifiedQty");
        }
        if (defect == null) {
            missing.add("defectQty");
        }
        List<String> warnings = new ArrayList<>();
        if (qty != null && qualified != null && defect != null && qualified + defect != qty) {
            warnings.add("合格数量与不良数量之和不等于报工数量");
        }

        String operator = firstText(OPERATOR, text);
        String remark = text.length() > 200 ? text.substring(0, 200) : text;
        return new ReportDraft(qty, qualified, defect, operator, remark, missing, warnings, true);
    }

    @Override
    public String proposeSql(AiQueryContext context) {
        String question = context.question() == null ? "" : context.question();
        if (question.contains("设备") || question.contains("在线")) {
            return "SELECT device_no, device_name, device_type, status, workshop, last_seen_at "
                    + "FROM v_device_status";
        }
        if (question.contains("质量") || question.contains("不良") || question.contains("质检")) {
            return "SELECT order_no, product_name, result, defect_desc, inspect_time, workshop "
                    + "FROM v_quality_summary";
        }
        if (question.contains("计划") || question.contains("进度") || question.contains("交期")) {
            return "SELECT plan_order_no, product_name, plan_qty, done_qty, status, workshop "
                    + "FROM v_plan_progress";
        }
        if (question.contains("物料") || question.contains("库存") || question.contains("齐套")) {
            return "SELECT mat_code, mat_name, stock_qty, safe_stock, kit_status, below_safe "
                    + "FROM v_kit_status";
        }
        return "SELECT order_no, product_name, qty, qty_done, status, workshop "
                + "FROM v_work_order_summary";
    }

    private Integer firstNumber(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private String firstText(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}
