package com.zhizao.service;

import com.zhizao.common.BusinessException;
import com.zhizao.dto.QcRequest;
import com.zhizao.dto.ReportRequest;
import com.zhizao.entity.Process;
import com.zhizao.entity.QualityRecord;
import com.zhizao.entity.WorkOrder;
import com.zhizao.entity.WorkOrderReport;
import com.zhizao.mapper.PlanMapper;
import com.zhizao.mapper.QualityRecordMapper;
import com.zhizao.mapper.WorkOrderMapper;
import com.zhizao.mapper.WorkOrderReportMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {

    @Mock
    private WorkOrderMapper workOrderMapper;
    @Mock
    private WorkOrderReportMapper workOrderReportMapper;
    @Mock
    private QualityRecordMapper qualityRecordMapper;
    @Mock
    private PlanMapper planMapper;
    @Mock
    private KitCheckService kitCheckService;
    @Mock
    private ProcessService processService;
    @Mock
    private CodeGenerator codeGenerator;

    private WorkOrderService workOrderService;

    @BeforeEach
    void setUp() {
        workOrderService = new WorkOrderService(
                workOrderMapper, workOrderReportMapper, qualityRecordMapper, planMapper,
                new WorkOrderStateMachine(), kitCheckService, processService, codeGenerator);
    }

    private WorkOrder order(String status) {
        WorkOrder order = new WorkOrder();
        order.setId(1L);
        order.setStatus(status);
        order.setProcessId(2L);
        order.setPlanId(3L);
        order.setQty(10);
        return order;
    }

    private Process publishedProcess() {
        Process process = new Process();
        process.setApprovalStatus("PUBLISHED");
        return process;
    }

    @Test
    void startSuccess() {
        WorkOrder order = order("PENDING");
        when(workOrderMapper.selectById(1L)).thenReturn(order);
        when(processService.getById(2L)).thenReturn(publishedProcess());
        when(kitCheckService.check(order)).thenReturn(KitCheckResult.pass());

        WorkOrder result = workOrderService.start(1L, "zhangsan");

        assertEquals("IN_PRODUCTION", result.getStatus());
        verify(workOrderMapper).updateById(order);
    }

    @Test
    void startFailWhenProcessNotPublished() {
        WorkOrder order = order("PENDING");
        when(workOrderMapper.selectById(1L)).thenReturn(order);
        Process draft = new Process();
        draft.setApprovalStatus("DRAFT");
        when(processService.getById(2L)).thenReturn(draft);

        assertThrows(BusinessException.class, () -> workOrderService.start(1L, "zhangsan"));
    }

    @Test
    void startFailWhenKitNotPassed() {
        WorkOrder order = order("PENDING");
        when(workOrderMapper.selectById(1L)).thenReturn(order);
        when(processService.getById(2L)).thenReturn(publishedProcess());
        when(kitCheckService.check(order)).thenReturn(new KitCheckResult(false,
                List.of(new KitCheckResult.ShortageItem("MT-001", "45#圆钢", 10, 5))));

        assertThrows(BusinessException.class, () -> workOrderService.start(1L, "zhangsan"));
    }

    @Test
    void reportSuccess() {
        WorkOrder order = order("IN_PRODUCTION");
        when(workOrderMapper.selectById(1L)).thenReturn(order);
        when(codeGenerator.next("RP")).thenReturn("RP-202609-0001");

        WorkOrder result = workOrderService.report(1L, new ReportRequest(10, 9, 1, null, "zhangsan"));

        assertEquals("PENDING_QC", result.getStatus());
        verify(workOrderReportMapper).insert(any(WorkOrderReport.class));
        verify(workOrderMapper).updateById(order);
    }

    @Test
    void reportFailWhenWrongStatus() {
        WorkOrder order = order("PENDING");
        when(workOrderMapper.selectById(1L)).thenReturn(order);

        assertThrows(BusinessException.class,
                () -> workOrderService.report(1L, new ReportRequest(10, 9, 1, null, "zhangsan")));
    }

    @Test
    void qcPassFinishesOrder() {
        WorkOrder order = order("PENDING_QC");
        when(workOrderMapper.selectById(1L)).thenReturn(order);
        when(codeGenerator.next("QC")).thenReturn("QC-202609-0001");
        when(workOrderMapper.selectCount(any())).thenReturn(1L);

        WorkOrder result = workOrderService.qc(1L, new QcRequest("PASS", null, "lisi"));

        assertEquals("FINISHED", result.getStatus());
        verify(qualityRecordMapper).insert(any(QualityRecord.class));
        verify(workOrderMapper).updateById(order);
    }

    @Test
    void qcFailReturnsToProduction() {
        WorkOrder order = order("PENDING_QC");
        when(workOrderMapper.selectById(1L)).thenReturn(order);
        when(codeGenerator.next("QC")).thenReturn("QC-202609-0001");

        WorkOrder result = workOrderService.qc(1L, new QcRequest("FAIL", "尺寸超差", "lisi"));

        assertEquals("IN_PRODUCTION", result.getStatus());
    }

    @Test
    void qcInvalidResultThrows() {
        WorkOrder order = order("PENDING_QC");
        when(workOrderMapper.selectById(1L)).thenReturn(order);
        when(codeGenerator.next("QC")).thenReturn("QC-202609-0001");

        assertThrows(BusinessException.class,
                () -> workOrderService.qc(1L, new QcRequest("OTHER", null, "lisi")));
    }
}
