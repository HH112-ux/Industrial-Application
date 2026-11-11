package com.zhizao.controller;

import com.zhizao.common.Result;
import com.zhizao.entity.AlarmRule;
import com.zhizao.service.AlarmRuleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 报警规则接口：仅管理员可维护阈值规则。
 *
 * @author Caspian
 */
@Tag(name = "报警规则")
@RestController
@RequestMapping("/api/alarm-rules")
@PreAuthorize("hasRole('ADMIN')")
public class AlarmRuleController {

    private final AlarmRuleService ruleService;

    public AlarmRuleController(AlarmRuleService ruleService) {
        this.ruleService = ruleService;
    }
    @GetMapping
    public Result<List<AlarmRule>> list(@RequestParam(required = false) Long deviceId) {
        return Result.ok(ruleService.list(deviceId));
    }
    @PostMapping
    public Result<AlarmRule> create(@RequestBody AlarmRule rule) {
        return Result.ok(ruleService.create(rule));
    }
    @PutMapping("/{id}")
    public Result<AlarmRule> update(@PathVariable Long id, @RequestBody AlarmRule rule) {
        return Result.ok(ruleService.update(id, rule));
    }
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ruleService.delete(id);
        return Result.ok();
    }
}
