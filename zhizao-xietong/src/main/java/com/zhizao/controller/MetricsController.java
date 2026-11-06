package com.zhizao.controller;

import com.zhizao.common.BusinessMetrics;
import com.zhizao.common.Result;
import com.zhizao.dto.MetricsOverview;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 业务指标查询。原始计数与耗时同时暴露在 actuator，这里提供带口径说明的聚合视图。
 *
 * @author Caspian
 */
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final BusinessMetrics businessMetrics;

    /**
     * 构造指标控制器。
     *
     * @param businessMetrics 业务指标
     */
    public MetricsController(BusinessMetrics businessMetrics) {
        this.businessMetrics = businessMetrics;
    }

    /**
     * 查询设备在线率、遥测上报、报工、工单节拍与齐套率。
     *
     * @return 指标概览，空分母项返回 {@code null} 与原因
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/overview")
    public Result<MetricsOverview> overview() {
        return Result.ok(businessMetrics.overview());
    }
}
