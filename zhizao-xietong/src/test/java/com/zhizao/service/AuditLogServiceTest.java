package com.zhizao.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhizao.entity.AuditLog;
import com.zhizao.mapper.AuditLogMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    static {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AuditLog.class);
    }

    @Mock
    private AuditLogMapper auditLogMapper;

    @InjectMocks
    private AuditLogService auditLogService;

    @Test
    @SuppressWarnings("unchecked")
    void listAppliesUserUriAndInclusiveDateRange() {
        auditLogService.list(1, 10, "zhang", "/api/work-orders", LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 2));

        ArgumentCaptor<LambdaQueryWrapper<AuditLog>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(auditLogMapper).selectPage(any(), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("username"));
        assertTrue(sql.contains("uri"));
        assertTrue(sql.contains("created_at"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("zhang%"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("/api/work-orders%"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(LocalDate.of(2026, 3, 1).atStartOfDay()));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(LocalDate.of(2026, 3, 3).atStartOfDay()));
    }
}
