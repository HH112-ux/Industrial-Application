package com.zhizao.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhizao.common.BusinessException;
import com.zhizao.common.ForbiddenException;
import com.zhizao.entity.Device;
import com.zhizao.mapper.DeviceMapper;
import com.zhizao.security.DataScope;
import com.zhizao.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    static {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Device.class);
    }

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private CodeGenerator codeGenerator;

    private DeviceService deviceService() {
        return new DeviceService(deviceMapper, codeGenerator, new DataScope());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String role, String workshopScope) {
        UserPrincipal principal = new UserPrincipal("zhangsan", role, workshopScope);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listFiltersByWorkshopScope() {
        loginAs("USER", "二车间");

        deviceService().list(1, 10, null, null);

        ArgumentCaptor<LambdaQueryWrapper<Device>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(deviceMapper).selectPage(any(), captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("workshop"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("二车间"));
    }

    @Test
    void detailRejectsDeviceFromAnotherWorkshop() {
        loginAs("USER", "二车间");
        Device device = new Device();
        device.setId(1L);
        device.setWorkshop("一车间");
        when(deviceMapper.selectById(1L)).thenReturn(device);

        assertThrows(ForbiddenException.class, () -> deviceService().getById(1L));
    }

    @Test
    void detailAllowsDeviceFromOwnWorkshop() {
        loginAs("USER", "二车间");
        Device device = new Device();
        device.setId(1L);
        device.setWorkshop("二车间");
        when(deviceMapper.selectById(1L)).thenReturn(device);

        assertEquals("二车间", deviceService().getById(1L).getWorkshop());
    }

    @Test
    void createRejectsMissingWorkshop() {
        when(codeGenerator.next("EQ")).thenReturn("EQ-202611-0001");

        BusinessException error = assertThrows(BusinessException.class,
                () -> deviceService().create(new Device()));

        assertEquals("设备必须指定车间", error.getMessage());
        verify(deviceMapper, never()).insert(any(Device.class));
    }

    @Test
    void createTrimsProvidedWorkshop() {
        when(codeGenerator.next("EQ")).thenReturn("EQ-202611-0001");
        Device device = new Device();
        device.setDeviceName("数控车床");
        device.setWorkshop("  三车间  ");

        Device saved = deviceService().create(device);

        assertEquals("三车间", saved.getWorkshop());
        verify(deviceMapper).insert(device);
    }

    @Test
    void adminSeesDeviceFromAnyWorkshop() {
        loginAs("ADMIN", null);
        Device device = new Device();
        device.setId(1L);
        device.setWorkshop("一车间");
        when(deviceMapper.selectById(1L)).thenReturn(device);

        assertEquals("一车间", deviceService().getById(1L).getWorkshop());
    }
}
