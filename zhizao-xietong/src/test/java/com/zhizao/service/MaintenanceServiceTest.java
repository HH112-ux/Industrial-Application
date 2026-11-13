package com.zhizao.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhizao.common.BusinessException;
import com.zhizao.common.ForbiddenException;
import com.zhizao.common.PageResult;
import com.zhizao.entity.Device;
import com.zhizao.entity.MaintenanceRecord;
import com.zhizao.mapper.DeviceMapper;
import com.zhizao.mapper.MaintenanceRecordMapper;
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
 * 点检维修服务测试：车间数据域过滤与跨车间读写拒绝。
 *
 * @author Caspian
 */
@ExtendWith(MockitoExtension.class)
class MaintenanceServiceTest {

    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MaintenanceRecord.class);
        TableInfoHelper.initTableInfo(assistant, Device.class);
    }

    @Mock
    private MaintenanceRecordMapper maintenanceRecordMapper;
    @Mock
    private DeviceMapper deviceMapper;
    @Mock
    private CodeGenerator codeGenerator;

    private MaintenanceService service() {
        return new MaintenanceService(maintenanceRecordMapper, deviceMapper, codeGenerator, new DataScope());
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

    private static MaintenanceRecord record(Long id, Long deviceId) {
        MaintenanceRecord record = new MaintenanceRecord();
        record.setId(id);
        record.setDeviceId(deviceId);
        return record;
    }

    @Test
    @SuppressWarnings("unchecked")
    void listFiltersByWorkshopScope() {
        loginAs("USER", "二车间");
        when(deviceMapper.selectList(any())).thenReturn(List.of(device(2L, "二车间")));
        doAnswer(inv -> {
            Page<MaintenanceRecord> page = inv.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(record(9L, 2L)));
            return page;
        }).when(maintenanceRecordMapper).selectPage(any(), any());

        PageResult<MaintenanceRecord> result = service().list(1, 10, null, null);

        assertEquals(1, result.getTotal());
        ArgumentCaptor<LambdaQueryWrapper<MaintenanceRecord>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(maintenanceRecordMapper).selectPage(any(), captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("device_id"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(2L));
    }

    @Test
    void listReturnsEmptyWithoutQueryWhenWorkshopUnassigned() {
        loginAs("USER", "");

        PageResult<MaintenanceRecord> result = service().list(1, 10, null, null);

        assertTrue(result.getRecords().isEmpty());
        assertEquals(0, result.getTotal());
        verifyNoInteractions(maintenanceRecordMapper, deviceMapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listAdminIsNotFilteredByWorkshop() {
        loginAs("ADMIN", null);
        doAnswer(inv -> {
            Page<MaintenanceRecord> page = inv.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(record(9L, 2L)));
            return page;
        }).when(maintenanceRecordMapper).selectPage(any(), any());

        service().list(1, 10, null, null);

        verify(deviceMapper, never()).selectList(any());
        ArgumentCaptor<LambdaQueryWrapper<MaintenanceRecord>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(maintenanceRecordMapper).selectPage(any(), captor.capture());
        assertFalse(captor.getValue().getSqlSegment().contains("device_id"));
    }

    @Test
    void detailRejectsRecordFromAnotherWorkshop() {
        loginAs("USER", "二车间");
        when(maintenanceRecordMapper.selectById(9L)).thenReturn(record(9L, 2L));
        when(deviceMapper.selectById(2L)).thenReturn(device(2L, "一车间"));

        ForbiddenException error = assertThrows(ForbiddenException.class, () -> service().getById(9L));

        assertEquals("无权访问其他车间的数据", error.getMessage());
    }

    @Test
    void detailReturnsRecordInOwnWorkshop() {
        loginAs("USER", "二车间");
        when(maintenanceRecordMapper.selectById(9L)).thenReturn(record(9L, 2L));
        when(deviceMapper.selectById(2L)).thenReturn(device(2L, "二车间"));

        assertEquals(9L, service().getById(9L).getId());
    }

    @Test
    void detailReturnsNotFoundWhenRecordMissing() {
        when(maintenanceRecordMapper.selectById(99L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class, () -> service().getById(99L));

        assertEquals(404, error.getCode());
        verifyNoInteractions(deviceMapper);
    }

    @Test
    void createRejectsDeviceFromAnotherWorkshop() {
        loginAs("USER", "二车间");
        when(deviceMapper.selectById(2L)).thenReturn(device(2L, "一车间"));

        assertThrows(ForbiddenException.class, () -> service().create(record(null, 2L)));
        verify(maintenanceRecordMapper, never()).insert(any(MaintenanceRecord.class));
        verifyNoInteractions(codeGenerator);
    }

    @Test
    void createRejectsWhenWorkshopUnassigned() {
        loginAs("USER", "");
        when(deviceMapper.selectById(2L)).thenReturn(device(2L, "一车间"));

        ForbiddenException error = assertThrows(ForbiddenException.class,
                () -> service().create(record(null, 2L)));

        assertEquals("当前账号未配置车间数据域", error.getMessage());
    }

    @Test
    void createRejectsMissingDevice() {
        loginAs("USER", "二车间");
        when(deviceMapper.selectById(404L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service().create(record(null, 404L)));

        assertEquals(404, error.getCode());
        verify(maintenanceRecordMapper, never()).insert(any(MaintenanceRecord.class));
    }

    @Test
    void createStoresRecordForScopedDevice() {
        loginAs("USER", "二车间");
        MaintenanceRecord input = record(null, 2L);
        when(deviceMapper.selectById(2L)).thenReturn(device(2L, "二车间"));
        when(codeGenerator.next("MN")).thenReturn("MN-202611-0001");

        MaintenanceRecord saved = service().create(input);

        assertEquals("MN-202611-0001", saved.getMtNo());
        assertEquals("NORMAL", saved.getResultStatus());
        assertNotNull(saved.getMtTime());
        verify(maintenanceRecordMapper).insert(input);
    }

    @Test
    void updateRejectsRecordFromAnotherWorkshop() {
        loginAs("USER", "二车间");
        when(maintenanceRecordMapper.selectById(9L)).thenReturn(record(9L, 2L));
        when(deviceMapper.selectById(2L)).thenReturn(device(2L, "一车间"));

        assertThrows(ForbiddenException.class, () -> service().update(9L, record(null, null)));
        verify(maintenanceRecordMapper, never()).updateById(any(MaintenanceRecord.class));
    }

    @Test
    void updateRejectsMovingRecordToAnotherWorkshop() {
        loginAs("USER", "二车间");
        when(maintenanceRecordMapper.selectById(9L)).thenReturn(record(9L, 2L));
        when(deviceMapper.selectById(2L)).thenReturn(device(2L, "二车间"));
        when(deviceMapper.selectById(3L)).thenReturn(device(3L, "一车间"));

        assertThrows(ForbiddenException.class, () -> service().update(9L, record(null, 3L)));
        verify(maintenanceRecordMapper, never()).updateById(any(MaintenanceRecord.class));
    }

    @Test
    void deleteRejectsRecordFromAnotherWorkshop() {
        loginAs("USER", "二车间");
        when(maintenanceRecordMapper.selectById(9L)).thenReturn(record(9L, 2L));
        when(deviceMapper.selectById(2L)).thenReturn(device(2L, "一车间"));

        assertThrows(ForbiddenException.class, () -> service().delete(9L));
        verify(maintenanceRecordMapper, never()).deleteById(any(Long.class));
    }
}
