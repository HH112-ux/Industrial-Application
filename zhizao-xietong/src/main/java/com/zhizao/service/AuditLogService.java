package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhizao.common.PageResult;
import com.zhizao.entity.AuditLog;
import com.zhizao.mapper.AuditLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

/**
 * 审计日志查询：按操作人、路径与时间范围筛选，按时间倒序返回。
 *
 * @author Caspian
 */
@Service
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    /**
     * 构造审计查询服务。
     *
     * @param auditLogMapper 审计日志数据访问
     */
    public AuditLogService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    /**
     * 分页查询审计日志。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param username 操作人前缀匹配，可为空
     * @param uri      请求路径前缀匹配，可为空
     * @param from     起始日期（含），可为空
     * @param to       结束日期（含），可为空
     * @return 审计日志分页结果
     */
    public PageResult<AuditLog> list(int pageNum, int pageSize, String username, String uri,
                                     LocalDate from, LocalDate to) {
        Page<AuditLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AuditLog> qw = new LambdaQueryWrapper<>();
        qw.likeRight(StringUtils.hasText(username), AuditLog::getUsername, username);
        qw.likeRight(StringUtils.hasText(uri), AuditLog::getUri, uri);
        qw.ge(from != null, AuditLog::getCreatedAt, from != null ? from.atStartOfDay() : null);
        qw.lt(to != null, AuditLog::getCreatedAt, to != null ? to.plusDays(1).atStartOfDay() : null);
        qw.orderByDesc(AuditLog::getCreatedAt).orderByDesc(AuditLog::getId);
        auditLogMapper.selectPage(page, qw);
        return PageResult.of(page.getTotal(), page.getRecords());
    }
}
