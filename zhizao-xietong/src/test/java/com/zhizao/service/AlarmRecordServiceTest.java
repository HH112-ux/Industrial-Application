package com.zhizao.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhizao.common.BusinessException;
import com.zhizao.common.PageResult;
import com.zhizao.entity.AlarmRecord;
import com.zhizao.entity.Device;
import com.zhizao.mapper.AlarmRecordMapper;
import com.zhizao.mapper.DeviceMapper;
import com.zhizao.security.DataScope;
import com.zhizao.security.UserPrincipal;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 报警中心服务测试：车间数据域、未读数、确认与恢复的并发前置条件。
 *
 * @author Caspian
 */
@ExtendWith(MockitoExtension.class)
class AlarmRecordServiceTest {

    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AlarmRecord.class);
        TableInfoHelper.initTableInfo(assistant, Device.class);
    }

    @Mock
    private AlarmRecordMapper recordMapper;
    @Mock
    private DeviceMapper deviceMapper;

    private AlarmRecordService service() {
        return new AlarmRecordService(recordMapper, deviceMapper, new DataScope());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String role, String scope) {
        UserPrincipal principal = new UserPrincipal("lisi", role, scope);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private static Device device(long id, String workshop) {
        Device device = new Device();
        device.setId(id);
        device.setWorkshop(workshop);
        return device;
    }

    private static AlarmRecord record(long id, long deviceId, String status) {
        AlarmRecord record = new AlarmRecord();
        record.setId(id);
        record.setDeviceId(deviceId);
        record.setMetric("temperature");
        record.setRuleType("THRESHOLD");
        record.setStatus(status);
        return record;
    }

    @Test
    @SuppressWarnings("unchecked")
    void listFiltersByWorkshopScope() {
        loginAs("USER", "二车间");
        when(deviceMapper.selectList(any())).thenReturn(List.of(device(2L, "二车间")));
        doAnswer(inv -> {
            Page<AlarmRecord> page = inv.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(record(9L, 2L, "OPEN")));
            return page;
        }).when(recordMapper).selectPage(any(), any());

        PageResult<AlarmRecord> result = service().list(1, 10, "OPEN");

        assertEquals(1, result.getTotal());
        assertEquals(9L, result.getRecords().get(0).getId());
        ArgumentCaptor<LambdaQueryWrapper<AlarmRecord>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(recordMapper).selectPage(any(), captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("device_id"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(2L));
    }

    @Test
    void listAndUnreadReturnEmptyWhenWorkshopUnassigned() {
        loginAs("USER", "");

        assertTrue(service().list(1, 10, null).getRecords().isEmpty());
        assertEquals(0, service().unreadCount());
        verifyNoInteractions(recordMapper, deviceMapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void unreadCountOnlyCountsOpenRecords() {
        loginAs("USER", "二车间");
        when(deviceMapper.selectList(any())).thenReturn(List.of(device(2L, "二车间")));
        when(recordMapper.selectCount(any())).thenReturn(2L);

        assertEquals(2, service().unreadCount());

        ArgumentCaptor<LambdaQueryWrapper<AlarmRecord>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(recordMapper).selectCount(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("status"));
        assertTrue(captor.getValue().getSqlSegment().contains("device_id"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("OPEN"));
    }

    @Test
    void acknowledgeRejectsSecondUser() {
        loginAs("USER", "二车间");
        when(recordMapper.selectById(9L)).thenReturn(record(9L, 2L, "OPEN"), record(9L, 2L, "ACKNOWLEDGED"));
        when(deviceMapper.selectById(2L)).thenReturn(device(2L, "二车间"));
        when(recordMapper.update(isNull(), any())).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class, () -> service().acknowledge(9L, "wangwu"));

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("他人处理"));
    }

    @Test
    void acknowledgeThenRecoverWriteBackStatus() {
        loginAs("USER", "二车间");
        AlarmRecord acked = record(9L, 2L, "ACKNOWLEDGED");
        acked.setAckUser("lisi");
        when(recordMapper.selectById(9L))
                .thenReturn(record(9L, 2L, "OPEN"), acked, acked, record(9L, 2L, "RECOVERED"));
        when(deviceMapper.selectById(2L)).thenReturn(device(2L, "二车间"));
        when(recordMapper.update(isNull(), any())).thenReturn(1);

        AlarmRecord acknowledged = service().acknowledge(9L, "lisi");
        AlarmRecord recovered = service().recover(9L);

        assertEquals("ACKNOWLEDGED", acknowledged.getStatus());
        assertEquals("lisi", acknowledged.getAckUser());
        assertEquals("RECOVERED", recovered.getStatus());
    }

    @Test
    void recoverRejectsRecoveredRecord() {
        loginAs("USER", "二车间");
        when(recordMapper.selectById(9L)).thenReturn(record(9L, 2L, "OPEN"), record(9L, 2L, "RECOVERED"));
        when(deviceMapper.selectById(2L)).thenReturn(device(2L, "二车间"));
        when(recordMapper.update(isNull(), any())).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class, () -> service().recover(9L));

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("已恢复"));
    }
}
