package com.zhizao.controller;

import com.zhizao.common.Result;
import com.zhizao.dto.ai.AiQueryRequest;
import com.zhizao.dto.ai.AiQueryResult;
import com.zhizao.dto.ai.AiResult;
import com.zhizao.dto.ai.DeviceIssueRequest;
import com.zhizao.dto.ai.DeviceIssueSuggestion;
import com.zhizao.dto.ai.ReportDraft;
import com.zhizao.dto.ai.ReportDraftRequest;
import com.zhizao.security.UserPrincipal;
import com.zhizao.service.AiService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 旁路接口：设备异常归因、自然语言报工草稿、只读数据问答。
 *
 * <p>三个端点都不写业务数据；未启用时统一返回 HTTP 503 与业务码 503。</p>
 *
 * @author Caspian
 */
@Tag(name = "AI 旁路")
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    /**
     * 构造 AI 控制器。
     *
     * @param aiService AI 旁路服务
     */
    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * 设备异常归因建议。
     *
     * @param request 设备与时间窗
     * @return 建议或不可用说明
     */
    @PostMapping("/device-issue/suggest")
    public Result<AiResult<DeviceIssueSuggestion>> suggestDeviceIssue(@RequestBody DeviceIssueRequest request) {
        return Result.ok(aiService.suggestDeviceIssue(request, currentUsername()));
    }

    /**
     * 自然语言报工草稿。草稿需人工确认后调用现有报工接口。
     *
     * @param request 工单与描述
     * @return 表单草稿或不可用说明
     */
    @PostMapping("/report/draft")
    public Result<AiResult<ReportDraft>> draftReport(@RequestBody ReportDraftRequest request) {
        return Result.ok(aiService.draftReport(request, currentUsername()));
    }

    /**
     * 生产数据问答，仅 ADMIN。
     *
     * @param request 问题
     * @return 只读查询结果或不可用说明
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/query")
    public Result<AiResult<AiQueryResult>> query(@RequestBody AiQueryRequest request) {
        return Result.ok(aiService.query(request, currentPrincipal()));
    }

    private String currentUsername() {
        UserPrincipal principal = currentPrincipal();
        return principal == null ? "unknown" : principal.username();
    }

    private UserPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UserPrincipal principal ? principal : null;
    }
}
