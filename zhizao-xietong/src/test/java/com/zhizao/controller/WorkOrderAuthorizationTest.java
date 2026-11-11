package com.zhizao.controller;

import com.zhizao.common.PageResult;
import com.zhizao.entity.SysUser;
import com.zhizao.entity.WorkOrder;
import com.zhizao.mapper.SysUserMapper;
import com.zhizao.security.JwtAuthenticationFilter;
import com.zhizao.security.JwtUtil;
import com.zhizao.security.SecurityConfig;
import com.zhizao.common.GlobalExceptionHandler;
import com.zhizao.service.PlanService;
import com.zhizao.service.WorkOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 工单写操作的角色控制：开工与报工对一线开放，质检只给管理员与普通用户，下达工单仅管理员。
 *
 * @author Caspian
 */
@WebMvcTest(controllers = {WorkOrderController.class, PlanController.class})
@ContextConfiguration(classes = WorkOrderAuthorizationTest.EmptyConfig.class)
@Import({WorkOrderController.class, PlanController.class, SecurityConfig.class,
        JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class WorkOrderAuthorizationTest {

    /**
     * 隔离主应用的 @MapperScan，避免 MVC 切片测试拉起数据库映射。
     */
    @SpringBootConfiguration
    static class EmptyConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkOrderService workOrderService;

    @MockBean
    private PlanService planService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private SysUserMapper userMapper;

    /**
     * 构造已启用、令牌版本匹配的指定角色用户。
     *
     * @param role 角色取值
     * @return 用户实体
     */
    private SysUser operator(String role) {
        SysUser user = new SysUser();
        user.setId(2L);
        user.setUsername("operator");
        user.setRole(role);
        user.setEnabled(1);
        user.setTokenVersion(0);
        return user;
    }

    /**
     * 让下一次请求以指定角色通过认证。
     *
     * @param role 角色取值
     */
    private void authenticateAs(String role) {
        when(jwtUtil.validate("token")).thenReturn(true);
        when(jwtUtil.getUsername("token")).thenReturn("operator");
        when(jwtUtil.getTokenVersion("token")).thenReturn(0);
        when(userMapper.selectOne(any())).thenReturn(operator(role));
    }

    private WorkOrder workOrder() {
        WorkOrder order = new WorkOrder();
        order.setId(1L);
        order.setOrderNo("WO-001");
        return order;
    }

    @Test
    void workerCanStartWorkOrder() throws Exception {
        authenticateAs("WORKER");
        when(workOrderService.start(eq(1L), anyString())).thenReturn(workOrder());

        mockMvc.perform(post("/api/work-orders/1/start").header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    void workerCanReportWorkOrder() throws Exception {
        authenticateAs("WORKER");
        when(workOrderService.report(eq(1L), any(), anyString())).thenReturn(workOrder());

        mockMvc.perform(post("/api/work-orders/1/report").header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qty\":10,\"qualifiedQty\":10,\"defectQty\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    void workerCannotQcWorkOrder() throws Exception {
        authenticateAs("WORKER");

        mockMvc.perform(post("/api/work-orders/1/qc").header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"PASS\"}"))
                .andExpect(status().isForbidden());

        verify(workOrderService, never()).qc(any(), any(), anyString());
    }

    @Test
    void userCanQcWorkOrder() throws Exception {
        authenticateAs("USER");
        when(workOrderService.qc(eq(1L), any(), anyString())).thenReturn(workOrder());

        mockMvc.perform(post("/api/work-orders/1/qc").header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"PASS\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanQcWorkOrder() throws Exception {
        authenticateAs("ADMIN");
        when(workOrderService.qc(eq(1L), any(), anyString())).thenReturn(workOrder());

        mockMvc.perform(post("/api/work-orders/1/qc").header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"FAIL\",\"defectDesc\":\"尺寸超差\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void workerCannotDispatchPlan() throws Exception {
        authenticateAs("WORKER");

        mockMvc.perform(post("/api/plans/1/dispatch").header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processId\":1}"))
                .andExpect(status().isForbidden());

        verify(planService, never()).dispatch(any(), any());
    }

    @Test
    void workerCanStillReadWorkOrders() throws Exception {
        authenticateAs("WORKER");
        when(workOrderService.list(anyInt(), anyInt(), any(), any()))
                .thenReturn(new PageResult<>(0L, List.of()));

        mockMvc.perform(get("/api/work-orders").header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }
}
