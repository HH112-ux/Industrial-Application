package com.zhizao.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

        planService.dispatch(1L, new DispatchRequest(5L, null, "二车间"));

        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderMapper).insert(captor.capture());
        assertEquals(Long.valueOf(5L), captor.getValue().getProcessId());
        assertEquals(Integer.valueOf(3), captor.getValue().getProcessVersion());
        assertEquals("二车间", captor.getValue().getWorkshop());
        assertEquals(Long.valueOf(9L), captor.getValue().getDeviceId());
        assertEquals("IN_PROGRESS", plan.getStatus());
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
