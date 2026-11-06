package com.zhizao.service;

import com.zhizao.entity.Plan;
import com.zhizao.entity.Process;
import com.zhizao.entity.WorkOrder;
import com.zhizao.mapper.PlanMapper;
import com.zhizao.mapper.ProcessMapper;
import com.zhizao.mapper.WorkOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock
    private PlanMapper planMapper;
    @Mock
    private ProcessMapper processMapper;
    @Mock
    private WorkOrderMapper workOrderMapper;
    @Mock
    private CodeGenerator codeGenerator;

    @InjectMocks
    private PlanService planService;

    @Test
    void dispatchLocksPublishedProcessVersion() {
        Plan plan = new Plan();
        plan.setId(1L);
        plan.setStatus("PENDING_SCHEDULE");
        plan.setProductCode("P-001");
        plan.setProductName("传动轴");
        plan.setPlanQty(10);
        when(planMapper.selectById(1L)).thenReturn(plan);
        when(codeGenerator.next("WO")).thenReturn("WO-202611-0001");

        Process process = new Process();
        process.setId(5L);
        process.setVersion(3);
        when(processMapper.selectOne(any())).thenReturn(process);

        planService.dispatch(1L);

        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderMapper).insert(captor.capture());
        assertEquals(Long.valueOf(5L), captor.getValue().getProcessId());
        assertEquals(Integer.valueOf(3), captor.getValue().getProcessVersion());
        assertEquals("IN_PROGRESS", plan.getStatus());
    }
}
