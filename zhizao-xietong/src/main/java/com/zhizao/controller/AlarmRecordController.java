package com.zhizao.controller;

import com.zhizao.common.PageResult;
import com.zhizao.common.Result;
import com.zhizao.entity.AlarmRecord;
import com.zhizao.security.UserPrincipal;
import com.zhizao.service.AlarmRecordService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报警中心接口：列表、未读数、确认与恢复，均按当前用户的车间数据域过滤。
 *
 * @author Caspian
 */
@Tag(name = "报警中心")
@RestController
@RequestMapping("/api/alarm-records")
public class AlarmRecordController {

    private final AlarmRecordService recordService;

    public AlarmRecordController(AlarmRecordService recordService) {
        this.recordService = recordService;
    }

    @GetMapping
    public Result<PageResult<AlarmRecord>> list(@RequestParam(defaultValue = "1") int pageNum,
                                                @RequestParam(defaultValue = "10") int pageSize,
                                                @RequestParam(required = false) String status) {
        return Result.ok(recordService.list(pageNum, pageSize, status));
    }

    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.ok(recordService.unreadCount());
    }

    @PostMapping("/{id}/acknowledge")
    public Result<AlarmRecord> acknowledge(@PathVariable Long id) {
        return Result.ok(recordService.acknowledge(id, currentUsername()));
    }

    @PostMapping("/{id}/recover")
    public Result<AlarmRecord> recover(@PathVariable Long id) {
        return Result.ok(recordService.recover(id));
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UserPrincipal principal
                ? principal.username() : "unknown";
    }
}
