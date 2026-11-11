package com.zhizao.service;

import com.zhizao.ai.AiAssistant;
import com.zhizao.ai.AiAuditRepository;
import com.zhizao.ai.AiQueryExecutor;
import com.zhizao.ai.AiReadOnlyTools;
import com.zhizao.ai.ReadOnlySqlGuard;
import com.zhizao.ai.SqlRejectedException;
import com.zhizao.common.AiDisabledException;
import com.zhizao.common.BadRequestException;
import com.zhizao.common.ForbiddenException;
import com.zhizao.config.AiProperties;
import com.zhizao.dto.ai.AiQueryContext;
import com.zhizao.dto.ai.AiQueryRequest;
import com.zhizao.dto.ai.AiQueryResult;
import com.zhizao.dto.ai.AiResult;
import com.zhizao.dto.ai.DeviceIssueContext;
import com.zhizao.dto.ai.DeviceIssueRequest;
import com.zhizao.dto.ai.DeviceIssueSuggestion;
import com.zhizao.dto.ai.ReportDraft;
import com.zhizao.dto.ai.ReportDraftContext;
import com.zhizao.dto.ai.ReportDraftRequest;
import com.zhizao.entity.WorkOrder;
import com.zhizao.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * AI 旁路编排：开关门禁、只读取数、模型调用、输出校验、降级与留痕。
 *
 * <p>三条硬约束在这里落地：模型异常一律降级为「不可用」；模型输出只作为建议返回；
 * 不调用任何业务写方法。Text-to-SQL 额外要求管理员身份，并通过只读视图白名单执行。</p>
 *
 * @author Caspian
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final String DEFAULT_DISCLAIMER = "以上为系统建议，需人工确认";
    private static final String UNAVAILABLE = "AI 建议暂不可用";

    private final AiProperties properties;
    private final AiAssistant assistant;
    private final AiReadOnlyTools tools;
    private final ReadOnlySqlGuard sqlGuard;
    private final AiAuditRepository auditRepository;
    private final AiQueryExecutor queryExecutor;

    /**
     * 构造 AI 旁路服务。
     *
     * @param properties      AI 配置
     * @param assistant       模型端口
     * @param tools           只读工具
     * @param sqlGuard        只读 SQL 白名单
     * @param auditRepository 留痕仓库
     * @param queryExecutor   只读查询执行器
     */
    public AiService(AiProperties properties, AiAssistant assistant, AiReadOnlyTools tools,
                     ReadOnlySqlGuard sqlGuard, AiAuditRepository auditRepository, AiQueryExecutor queryExecutor) {
        this.properties = properties;
        this.assistant = assistant;
        this.tools = tools;
        this.sqlGuard = sqlGuard;
        this.auditRepository = auditRepository;
        this.queryExecutor = queryExecutor;
    }

    /**
     * 设备异常归因建议。只读设备、遥测、维修与质检记录，不修改设备状态。
     *
     * @param request  设备与时间窗
     * @param username 发起人，仅用于留痕
     * @return 建议或降级结果
     */
    public AiResult<DeviceIssueSuggestion> suggestDeviceIssue(DeviceIssueRequest request, String username) {
        requireEnabled();
        if (request == null || request.deviceId() == null) {
            throw new BadRequestException("设备 ID 不能为空");
        }
        DeviceIssueContext context = tools.loadDeviceIssue(request.deviceId(), request.from(), request.to());
        String target = String.valueOf(request.deviceId());
        try {
            DeviceIssueSuggestion safe = sanitizeSuggestion(assistant.suggestDeviceIssue(context));
            if (safe == null) {
                auditRepository.logSuggestion("DEVICE_ISSUE", username, target, evidenceCount(context),
                        null, "模型输出未通过校验，已降级", false);
                return AiResult.unavailable(UNAVAILABLE);
            }
            auditRepository.logSuggestion("DEVICE_ISSUE", username, target, evidenceCount(context),
                    safe.confidence(), summarize(safe), true);
            return AiResult.available(safe);
        } catch (RuntimeException e) {
            log.warn("AI device suggestion degraded: {}", e.getClass().getSimpleName());
            auditRepository.logSuggestion("DEVICE_ISSUE", username, target, evidenceCount(context),
                    null, "模型调用失败，已降级", false);
            return AiResult.unavailable(UNAVAILABLE);
        }
    }

    /**
     * 自然语言报工：只返回表单草稿，始终要求人工确认，不调用报工接口。
     *
     * @param request  工单与描述
     * @param username 发起人
     * @return 草稿或降级结果
     */
    public AiResult<ReportDraft> draftReport(ReportDraftRequest request, String username) {
        requireEnabled();
        if (request == null || request.workOrderId() == null) {
            throw new BadRequestException("工单 ID 不能为空");
        }
        if (!StringUtils.hasText(request.text())) {
            throw new BadRequestException("报工描述不能为空");
        }
        WorkOrder order = tools.loadWorkOrder(request.workOrderId());
        String target = String.valueOf(request.workOrderId());
        try {
            ReportDraft safe = sanitizeDraft(assistant.draftReport(new ReportDraftContext(order, request.text())), order);
            if (safe == null) {
                auditRepository.logSuggestion("REPORT_DRAFT", username, target, 0,
                        null, "模型未返回可用草稿，已降级", false);
                return AiResult.unavailable(UNAVAILABLE);
            }
            auditRepository.logSuggestion("REPORT_DRAFT", username, target, 0,
                    null, "报工草稿待人工确认", true);
            return AiResult.available(safe);
        } catch (RuntimeException e) {
            log.warn("AI report draft degraded: {}", e.getClass().getSimpleName());
            auditRepository.logSuggestion("REPORT_DRAFT", username, target, 0,
                    null, "模型调用失败，已降级", false);
            return AiResult.unavailable(UNAVAILABLE);
        }
    }

    /**
     * 生产数据问答：仅管理员，模型只生成只读 SQL，经白名单校验后在只读视图上执行。
     *
     * @param request   问题
     * @param principal 当前用户
     * @return 查询结果或降级结果
     */
    public AiResult<AiQueryResult> query(AiQueryRequest request, UserPrincipal principal) {
        requireEnabled();
        if (principal == null || !principal.isAdmin()) {
            throw new ForbiddenException("AI 数据问答仅对管理员开放");
        }
        if (request == null || !StringUtils.hasText(request.question())) {
            throw new BadRequestException("问题不能为空");
        }
        String username = principal.username();
        String question = request.question().trim();
        String candidate;
        try {
            candidate = assistant.proposeSql(new AiQueryContext(question));
        } catch (RuntimeException e) {
            log.warn("AI query degraded: {}", e.getClass().getSimpleName());
            auditRepository.logQuery(username, question, null, 0, 0, false, "MODEL_UNAVAILABLE");
            return AiResult.unavailable("AI 查询暂不可用");
        }

        String sql;
        try {
            sql = sqlGuard.validate(candidate);
        } catch (SqlRejectedException e) {
            auditRepository.logQuery(username, question, candidate, 0, 0, false, e.getMessage());
            return AiResult.unavailable("生成的查询未通过只读白名单校验");
        }

        long start = System.nanoTime();
        try {
            List<Map<String, Object>> rows = queryExecutor.query(sql,
                    properties.getQueryTimeoutSeconds(), properties.getMaxRows());
            long duration = elapsedMs(start);
            List<String> columns = rows.isEmpty() ? List.of() : List.copyOf(rows.get(0).keySet());
            auditRepository.logQuery(username, question, sql, rows.size(), duration, true, null);
            return AiResult.available(new AiQueryResult(sql, columns, rows, rows.size(), duration));
        } catch (RuntimeException e) {
            log.warn("AI query execution degraded: {}", e.getClass().getSimpleName());
            auditRepository.logQuery(username, question, sql, 0, elapsedMs(start), false, "QUERY_FAILED");
            return AiResult.unavailable("查询执行失败或超时，请缩小问题范围");
        }
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new AiDisabledException("AI 能力未启用（ai.enabled=false）");
        }
    }

    private DeviceIssueSuggestion sanitizeSuggestion(DeviceIssueSuggestion raw) {
        if (raw == null) {
            return null;
        }
        List<DeviceIssueSuggestion.Cause> causes = raw.causes() == null ? List.of() : raw.causes().stream()
                .filter(Objects::nonNull)
                .filter(cause -> StringUtils.hasText(cause.cause()) && StringUtils.hasText(cause.evidence()))
                .limit(10)
                .toList();
        List<String> actions = raw.actions() == null ? List.of() : raw.actions().stream()
                .filter(StringUtils::hasText)
                .limit(10)
                .toList();
        if (causes.isEmpty() && actions.isEmpty()) {
            return null;
        }
        if (!(raw.confidence() >= 0 && raw.confidence() <= 1)) {
            return null;
        }
        List<Long> refs = raw.maintenanceRefs() == null ? List.of() : raw.maintenanceRefs().stream()
                .filter(Objects::nonNull)
                .toList();
        String disclaimer = StringUtils.hasText(raw.disclaimer()) ? raw.disclaimer() : DEFAULT_DISCLAIMER;
        return new DeviceIssueSuggestion(causes, actions, refs, raw.confidence(), disclaimer);
    }

    private ReportDraft sanitizeDraft(ReportDraft raw, WorkOrder order) {
        if (raw == null) {
            return null;
        }
        Set<String> missing = new LinkedHashSet<>();
        if (raw.missingFields() != null) {
            raw.missingFields().stream()
                    .filter(field -> Set.of("qty", "qualifiedQty", "defectQty").contains(field))
                    .forEach(missing::add);
        }
        Integer qty = positive(raw.qty());
        if (qty == null) {
            missing.add("qty");
        }
        Integer qualified = nonNegative(raw.qualifiedQty());
        if (qualified == null) {
            missing.add("qualifiedQty");
        }
        Integer defect = nonNegative(raw.defectQty());
        if (defect == null) {
            missing.add("defectQty");
        }

        Set<String> warnings = new LinkedHashSet<>();
        if (raw.warnings() != null) {
            raw.warnings().stream().filter(StringUtils::hasText).limit(10).forEach(warnings::add);
        }
        if (qty != null && qualified != null && defect != null && qualified + defect != qty) {
            warnings.add("合格数量与不良数量之和不等于报工数量");
        }
        int remaining = remaining(order);
        if (qty != null && qty > remaining) {
            warnings.add("报工数量超过工单剩余数量 " + remaining);
        }
        String operator = trimTo(raw.operator(), 32);
        String remark = trimTo(raw.remark(), 255);
        return new ReportDraft(qty, qualified, defect, operator, remark,
                List.copyOf(missing), List.copyOf(warnings), true);
    }

    private Integer positive(Integer value) {
        return value == null || value <= 0 ? null : value;
    }

    /**
     * 合格数与不良数允许为 0：只有模型没有给出数值才算「缺失」。
     *
     * <p>历史实现用 {@link #positive(Integer)} 处理不良数，把「填了 0」误判成「未填写」，
     * 前端会提示补填不良数；这里单独放开 0，负数仍视为非法（按缺失处理）。</p>
     *
     * @param value 模型给出的数量，可为空
     * @return 非负数量；为空或负数时返回 null
     */
    private Integer nonNegative(Integer value) {
        return value == null || value < 0 ? null : value;
    }

    private int remaining(WorkOrder order) {
        int qty = order.getQty() == null ? 0 : order.getQty();
        int done = order.getQtyDone() == null ? 0 : order.getQtyDone();
        return Math.max(qty - done, 0);
    }

    private String trimTo(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private int evidenceCount(DeviceIssueContext context) {
        return context.recent().size() + context.maintenance().size() + context.qualityFail().size();
    }

    private String summarize(DeviceIssueSuggestion suggestion) {
        List<String> parts = new ArrayList<>();
        if (!suggestion.causes().isEmpty()) {
            parts.add(suggestion.causes().get(0).cause());
        }
        if (!suggestion.actions().isEmpty()) {
            parts.add(suggestion.actions().get(0));
        }
        String summary = String.join("；", parts);
        return summary.length() > 500 ? summary.substring(0, 500) : summary;
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
