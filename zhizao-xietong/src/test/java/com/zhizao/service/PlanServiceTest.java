package com.zhizao.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhizao.common.BusinessException;
import com.zhizao.common.ForbiddenException;
import com.zhizao.dto.DispatchRequest;
import com.zhizao.entity.Plan;
import com.zhizao.entity.Process;
import com.zhizao.entity.WorkOrder;
import com.zhizao.mapper.PlanMapper;
import com.zhizao.mapper.ProcessMapper;
import com.zhizao.mapper.WorkOrderMapper;
import com.zhizao.security.DataScope;
import com.zhizao.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    static {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Plan.class);
    }

    @Mock
    private PlanMapper planMapper;
    @Mock
    private ProcessMapper processMapper;
    @Mock
    private WorkOrderMapper workOrderMapper;
    @Mock
    private CodeGenerator codeGenerator;
    @Mock
    private DataScope dataScope;

    @InjectMocks
    private PlanService planService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String role, String workshopScope) {
        UserPrincipal principal = new UserPrincipal("zhangsan", role, workshopScope);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private PlanService scopedService() {
        return new PlanService(planMapper, processMapper, workOrderMapper, codeGenerator, new DataScope());
    }

    @Test
    void dispatchLocksPublishedProcessVersion() {
        Plan plan = new Plan();
        plan.setId(1L);
        plan.setStatus("PENDING_SCHEDULE");
        plan.setProductCode("P-001");
        plan.setProductName("传动轴");
        plan.setPlanQty(10);
        plan.setRequiredDeviceId(9L);
        when(planMapper.selectById(1L)).thenReturn(plan);
        when(codeGenerator.next("WO")).thenReturn("WO-202611-0001");

        Process process = new Process();
        process.setId(5L);
        process.setVersion(3);
        process.setApprovalStatus("PUBLISHED");
        when(processMapper.selectById(5L)).thenReturn(process);
        when(planMapper.update(any(), any())).thenReturn(1);

        planService.dispatch(1L, new DispatchRequest(5L, null, "二车间"));

        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderMapper).insert(captor.capture());
        assertEquals(Long.valueOf(5L), captor.getValue().getProcessId());
        assertEquals(Integer.valueOf(3), captor.getValue().getProcessVersion());
        assertEquals("二车间", captor.getValue().getWorkshop());
        assertEquals(Long.valueOf(9L), captor.getValue().getDeviceId());
        assertEquals("PLAN-1", captor.getValue().getDispatchToken());
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatchClaimsPlanWithConditionalStatusUpdate() {
        Plan plan = plan(1L);
        when(planMapper.selectById(1L)).thenReturn(plan);
        when(processMapper.selectById(5L)).thenReturn(publishedProcess());
        when(planMapper.update(any(), any())).thenReturn(1);
        when(codeGenerator.next("WO")).thenReturn("WO-202611-0001");

        planService.dispatch(1L, new DispatchRequest(5L, null, null));

        ArgumentCaptor<LambdaUpdateWrapper<Plan>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(planMapper).update(any(), captor.capture());
        LambdaUpdateWrapper<Plan> wrapper = captor.getValue();
        assertTrue(wrapper.getSqlSegment().contains("status"), "抢占必须是条件更新 status，而不是无条件写回");
        assertTrue(wrapper.getParamNameValuePairs().containsValue("PENDING_SCHEDULE"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("IN_PROGRESS"));
        verify(workOrderMapper).insert(any(WorkOrder.class));
    }

    @Test
    void dispatchRejectsWhenPlanAlreadyClaimed() {
        when(planMapper.selectById(1L)).thenReturn(plan(1L));
        when(processMapper.selectById(5L)).thenReturn(publishedProcess());
        when(planMapper.update(any(), any())).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> planService.dispatch(1L, new DispatchRequest(5L, null, null)));

        assertEquals(409, error.getCode());
        assertEquals("该计划已下达工单，请勿重复操作", error.getMessage());
        verify(workOrderMapper, never()).insert(any(WorkOrder.class));
    }

    @Test
    void dispatchConvertsDuplicateKeyToBusinessError() {
        when(planMapper.selectById(1L)).thenReturn(plan(1L));
        when(processMapper.selectById(5L)).thenReturn(publishedProcess());
        when(planMapper.update(any(), any())).thenReturn(1);
        when(codeGenerator.next("WO")).thenReturn("WO-202611-0001");
        doThrow(new DuplicateKeyException("uk_work_order_dispatch_token"))
                .when(workOrderMapper).insert(any(WorkOrder.class));

        BusinessException error = assertThrows(BusinessException.class,
                () -> planService.dispatch(1L, new DispatchRequest(5L, null, null)));

        assertEquals(409, error.getCode(), "唯一键冲突必须是业务冲突，不能暴露成 500");
        assertEquals("该计划已下达工单，请勿重复操作", error.getMessage());
    }

    @Test
    void concurrentDispatchCreatesSingleWorkOrder() {
        when(planMapper.selectById(1L)).thenReturn(plan(1L));
        when(processMapper.selectById(5L)).thenReturn(publishedProcess());
        when(codeGenerator.next("WO")).thenReturn("WO-202611-0001");
        // 第一个请求抢占成功，第二个请求的条件更新命中 0 行，模拟并发下数据库只放行一次
        when(planMapper.update(any(), any())).thenReturn(1, 0);

        assertNotNull(planService.dispatch(1L, new DispatchRequest(5L, null, null)));
        assertThrows(BusinessException.class,
                () -> planService.dispatch(1L, new DispatchRequest(5L, null, null)));

        verify(workOrderMapper, times(1)).insert(any(WorkOrder.class));
    }

    /** 构造待排产计划。 */
    private static Plan plan(Long id) {
        Plan plan = new Plan();
        plan.setId(id);
        plan.setStatus("PENDING_SCHEDULE");
        plan.setWorkshop("一车间");
        plan.setPlanQty(5);
        return plan;
    }

    /** 构造已发布工艺。 */
    private static Process publishedProcess() {
        Process process = new Process();
        process.setId(5L);
        process.setVersion(1);
        process.setApprovalStatus("PUBLISHED");
        return process;
    }

    @Test
    void dispatchRejectsDraftProcess() {
        Plan plan = new Plan();
        plan.setId(1L);
        plan.setStatus("PENDING_SCHEDULE");
        when(planMapper.selectById(1L)).thenReturn(plan);

        Process draft = new Process();
        draft.setId(5L);
        draft.setApprovalStatus("DRAFT");
        when(processMapper.selectById(5L)).thenReturn(draft);

        BusinessException error = assertThrows(BusinessException.class,
                () -> planService.dispatch(1L, new DispatchRequest(5L, null, null)));
        assertEquals("下达工单必须指定已发布工艺", error.getMessage());
    }

    @Test
    void dispatchRejectsMissingProcess() {
        Plan plan = new Plan();
        plan.setId(1L);
        plan.setStatus("PENDING_SCHEDULE");
        when(planMapper.selectById(1L)).thenReturn(plan);

        assertThrows(BusinessException.class,
                () -> planService.dispatch(1L, new DispatchRequest(null, null, null)));
    }

    @Test
    void createRejectsMissingWorkshop() {
        Plan plan = new Plan();
        plan.setProductCode("P-001");
        when(codeGenerator.next("PO")).thenReturn("PO-202611-0001");

        BusinessException error = assertThrows(BusinessException.class, () -> planService.create(plan));

        assertEquals("计划必须指定车间", error.getMessage());
        verify(planMapper, never()).insert(any(Plan.class));
    }

    @Test
    void createTrimsProvidedWorkshop() {
        Plan plan = new Plan();
        plan.setWorkshop("  二车间  ");
        when(codeGenerator.next("PO")).thenReturn("PO-202611-0001");

        Plan saved = planService.create(plan);

        assertEquals("二车间", saved.getWorkshop());
        verify(planMapper).insert(plan);
    }

    @Test
    void dispatchFallsBackToPlanWorkshop() {
        Plan plan = new Plan();
        plan.setId(1L);
        plan.setStatus("PENDING_SCHEDULE");
        plan.setWorkshop("二车间");
        plan.setPlanQty(5);
        when(planMapper.selectById(1L)).thenReturn(plan);
        when(codeGenerator.next("WO")).thenReturn("WO-202611-0001");

        Process process = new Process();
        process.setId(5L);
        process.setApprovalStatus("PUBLISHED");
        when(processMapper.selectById(5L)).thenReturn(process);
        when(planMapper.update(any(), any())).thenReturn(1);

        WorkOrder order = planService.dispatch(1L, new DispatchRequest(5L, null, null));

        assertEquals("二车间", order.getWorkshop());
    }

    @Test
    void dispatchRejectsWhenNoWorkshopAvailable() {
        Plan plan = new Plan();
        plan.setId(1L);
        plan.setStatus("PENDING_SCHEDULE");
        when(planMapper.selectById(1L)).thenReturn(plan);

        Process process = new Process();
        process.setId(5L);
        process.setApprovalStatus("PUBLISHED");
        when(processMapper.selectById(5L)).thenReturn(process);

        BusinessException error = assertThrows(BusinessException.class,
                () -> planService.dispatch(1L, new DispatchRequest(5L, null, "  ")));

        assertEquals("工单必须指定车间", error.getMessage());
        verify(workOrderMapper, never()).insert(any(WorkOrder.class));
    }

    @Test
    void deleteRejectsPlanWithMountedWorkOrders() {
        Plan plan = new Plan();
        plan.setId(1L);
        when(planMapper.selectById(1L)).thenReturn(plan);
        when(workOrderMapper.selectCount(any())).thenReturn(2L);

        BusinessException error = assertThrows(BusinessException.class, () -> planService.delete(1L));

        assertTrue(error.getMessage().contains("2 张工单"));
        verify(planMapper, never()).deleteById(1L);
    }

    @Test
    void deleteRemovesPlanWithoutWorkOrders() {
        Plan plan = new Plan();
        plan.setId(1L);
        when(planMapper.selectById(1L)).thenReturn(plan);
        when(workOrderMapper.selectCount(any())).thenReturn(0L);

        planService.delete(1L);

        verify(planMapper).deleteById(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listFiltersByWorkshopScope() {
        loginAs("USER", "二车间");

        scopedService().list(1, 10, null, null);

        ArgumentCaptor<LambdaQueryWrapper<Plan>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(planMapper).selectPage(any(), captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("workshop"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("二车间"));
    }

    @Test
    void detailRejectsPlanFromAnotherWorkshop() {
        loginAs("USER", "二车间");
        Plan plan = new Plan();
        plan.setId(1L);
        plan.setWorkshop("一车间");
        when(planMapper.selectById(1L)).thenReturn(plan);

        assertThrows(ForbiddenException.class, () -> scopedService().getById(1L));
    }

    @Test
    void adminSeesPlanFromAnyWorkshop() {
        loginAs("ADMIN", null);
        Plan plan = new Plan();
        plan.setId(1L);
        plan.setWorkshop("一车间");
        when(planMapper.selectById(1L)).thenReturn(plan);

        assertEquals("一车间", scopedService().getById(1L).getWorkshop());
    }
}
