package com.zhizao.controller;

import com.zhizao.common.PageResult;
import com.zhizao.common.Result;
import com.zhizao.dto.InboundCancelRequest;
import com.zhizao.dto.InboundConfirmRequest;
import com.zhizao.entity.FinishedGoodsInbound;
import com.zhizao.entity.FinishedGoodsStock;
import com.zhizao.service.FinishedGoodsInboundService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 成品入库接口：待入库查询、确认/取消入库与成品库存查询。查询沿用现有读权限，
 * 确认/取消需要能操作生产的角色，写操作由 AuditAspect 自动留痕。
 *
 * @author Caspian
 */
@Tag(name = "成品入库")
@RestController
@RequestMapping("/api/finished-goods")
public class FinishedGoodsInboundController {

    private final FinishedGoodsInboundService inboundService;

    public FinishedGoodsInboundController(FinishedGoodsInboundService inboundService) {
        this.inboundService = inboundService;
    }

    /**
     * 分页查询入库单，按车间数据域过滤。
     */
    @GetMapping("/inbounds")
    public Result<PageResult<FinishedGoodsInbound>> list(@RequestParam(defaultValue = "1") int pageNum,
                                                         @RequestParam(defaultValue = "10") int pageSize,
                                                         @RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(required = false) String workshop) {
        return Result.ok(inboundService.list(pageNum, pageSize, keyword, status, workshop));
    }

    /**
     * 取消待入库单，必须填写原因；并发冲突返回业务码 409。
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'WORKER')")
    @PostMapping("/inbounds/{id}/cancel")
    public Result<FinishedGoodsInbound> cancel(@PathVariable Long id,
                                               @RequestBody InboundCancelRequest request) {
        return Result.ok(inboundService.cancel(id, request.reason(), currentOperator()));
    }

    /**
     * 分页查询成品库存，按车间数据域过滤。
     */
    @GetMapping("/stocks")
    public Result<PageResult<FinishedGoodsStock>> stocks(@RequestParam(defaultValue = "1") int pageNum,
                                                         @RequestParam(defaultValue = "10") int pageSize,
                                                         @RequestParam(required = false) String keyword) {
        return Result.ok(inboundService.stockList(pageNum, pageSize, keyword));
    }

    /**
     * 确认入库，批次号可覆盖；被并发处理或数量超额时返回业务码 409。
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'WORKER')")
    @PostMapping("/inbounds/{id}/confirm")
    public Result<FinishedGoodsInbound> confirm(@PathVariable Long id,
                                                @RequestBody(required = false) InboundConfirmRequest request) {
        String batch = request == null ? null : request.batch();
        return Result.ok(inboundService.confirm(id, batch, currentOperator()));
    }

    /** 读取当前登录用户名，未认证时缺省为 system。 */
    private String currentOperator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && StringUtils.hasText(auth.getName()) ? auth.getName() : "system";
    }
}
