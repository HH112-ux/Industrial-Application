package com.zhizao.service;

import com.zhizao.common.BadRequestException;
import com.zhizao.common.BusinessException;
import com.zhizao.dto.QcRequest;
import com.zhizao.dto.ReportRequest;
import com.zhizao.entity.Process;
import com.zhizao.entity.QualityRecord;
import com.zhizao.entity.WorkOrder;
import com.zhizao.entity.WorkOrderReport;
import com.zhizao.entity.WorkOrderStatusHistory;
import com.zhizao.mapper.PlanMapper;
import com.zhizao.mapper.QualityRecordMapper;
import com.zhizao.mapper.WorkOrderMapper;
import com.zhizao.mapper.WorkOrderReportMapper;
import com.zhizao.mapper.WorkOrderStatusHistoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private WorkOrderStatusHistoryMapper statusHistoryMapper;
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
                statusHistoryMapper, new WorkOrderStateMachine(), kitCheckService, processService, codeGenerator);
    }

    private WorkOrder order(String status) {
        WorkOrder order = new WorkOrder();
        order.setId(1L);
        order.setStatus(status);
        order.setProcessId(2L);
        order.setPlanId(3L);
        order.setQty(10);
        order.setQtyDone(0);
        order.setQtyQualified(0);
        order.setQtyDefect(0);
        return order;
    }

    private Process publishedProcess() {
        Process process = new Process();
        process.setApprovalStatus("PUBLISHED");
        return process;
    }

    private WorkOrderStatusHistory capturedHistory() {
        ArgumentCaptor<WorkOrderStatusHistory> captor = ArgumentCaptor.forClass(WorkOrderStatusHistory.class);
        verify(statusHistoryMapper).insert(captor.capture());
        return captor.getValue();
    }

    @Test
    void startSuccess() {
        WorkOrder order = order("PENDING");
        when(workOrderMapper.selectById(1L)).thenReturn(order);
        when(processService.getById(2L)).thenReturn(publishedProcess());
        when(kitCheckService.check(order)).thenReturn(KitCheckResult.pass());
        when(workOrderMapper.updateById(order)).thenReturn(1);

        WorkOrder result = workOrderService.start(1L, "zhangsan");

        assertEquals("IN_PRODUCTION", result.getStatus());
        verify(workOrderMapper).updateById(order);
    }

    @Test
    void startWritesHistoryWithOperator() {
        WorkOrder order = order("PENDING");
        when(workOrderMapper.selectById(1L)).thenReturn(order);
        when(processService.getById(2L)).thenReturn(publishedProcess());
        when(kitCheckService.check(order)).thenReturn(KitCheckResult.pass());
        when(workOrderMapper.updateById(order)).thenReturn(1);

        workOrderService.start(1L, "zhangsan");

        WorkOrderStatusHistory history = capturedHistory();
        assertEquals(1L, history.getOrderId());
        assertEquals("PENDING", history.getFromStatus());
        assertEquals("IN_PRODUCTION", history.getToStatus());
        assertEquals("zhangsan", history.getOperator());
        assertEquals("开工", history.getRemark());
    }

    @Test
    void startConflictReturnsBusinessCode409WithoutHistory() {
        WorkOrder order = order("PENDING");
        when(workOrderMapper.selectById(1L)).thenReturn(order);
        when(processService.getById(2L)).thenReturn(publishedProcess());
        when(kitCheckService.check(order)).thenReturn(KitCheckResult.pass());
        when(workOrderMapper.updateById(order)).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> workOrderService.start(1L, "zhangsan"));

        assertEquals(409, error.getCode());
        verify(statusHistoryMapper, never()).insert(any(WorkOrderStatusHistory.class));
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
    void reportSuccessAccumulatesQuantityAndWritesHistory() {
        WorkOrder order = order("IN_PRODUCTION");
        when(workOrderMapper.selectById(1L)).thenReturn(order);
        when(codeGenerator.next("RP")).thenReturn("RP-202609-0001");
        when(workOrderMapper.updateById(order)).thenReturn(1);

        WorkOrder result = workOrderService.report(1L, new ReportRequest(10, 9, 1, null, "zhangsan", null));

        assertEquals("PENDING_QC", result.getStatus());
        assertEquals(10, result.getQtyDone());
        assertEquals(9, result.getQtyQualified());
        assertEquals(1, result.getQtyDefect());
        verify(workOrderReportMapper).insert(any(WorkOrderReport.class));
        WorkOrderStatusHistory history = capturedHistory();
        assertEquals("IN_PRODUCTION", history.getFromStatus());
        assertEquals("PENDING_QC", history.getToStatus());
        assertEquals("zhangsan", history.getOperator());
    }

    @Test
    void reportFailWhenWrongStatus() {
        WorkOrder order = order("PENDING");
        when(workOrderMapper.selectById(1L)).thenReturn(order);

        assertThrows(BusinessException.class,
                () -> workOrderService.report(1L, new ReportRequest(10, 9, 1, null, "zhangsan", null)));
    }

    @Test
    void reportRejectsNonPositiveQty() {
        WorkOrder order = order("IN_PRODUCTION");
        when(workOrderMapper.selectById(1L)).thenReturn(order);

        assertThrows(BadRequestException.class,
                () -> workOrderService.report(1L, new ReportRequest(0, 0, 0, null, "zhangsan", null)));
        verify(workOrderReportMapper, never()).insert(any(WorkOrderReport.class));
        verify(statusHistoryMapper, never()).insert(any(WorkOrderStatusHistory.class));
    }

    @Test
    void reportRejectsQuantityMismatch() {
        WorkOrder order = order("IN_PRODUCTION");
        when(workOrderMapper.selectById(1L)).thenReturn(order);

        assertThrows(BadRequestException.class,
                () -> workOrderService.report(1L, new ReportRequest(10, 8, 1, null, "zhangsan", null)));
        verify(workOrderReportMapper, never()).insert(any(WorkOrderReport.class));
    }

    @Test
    void reportRejectsQuantityOverRemaining() {
        WorkOrder order = order("IN_PRODUCTION");
        order.setQtyDone(8);
        when(workOrderMapper.selectById(1L)).thenReturn(order);

        BusinessException error = assertThrows(BusinessException.class,
                () -> workOrderService.report(1L, new ReportRequest(5, 5, 0, null, "zhangsan", null)));

        assertEquals(409, error.getCode());
        verify(workOrderReportMapper, never()).insert(any(WorkOrderReport.class));
        verify(statusHistoryMapper, never()).insert(any(WorkOrderStatusHistory.class));
    }

    @Test
    void reportDuplicateKeyReturnsSameResultWithoutSideEffects() {
        WorkOrder order = order("IN_PRODUCTION");
        when(workOrderMapper.selectById(1L)).thenReturn(order);
        when(codeGenerator.next("RP")).thenReturn("RP-202609-0001");
        when(workOrderMapper.updateById(order)).thenReturn(1);
        when(workOrderReportMapper.selectCount(any())).thenReturn(0L, 1L);
        ReportRequest req = new ReportRequest(10, 9, 1, null, "zhangsan", "7f0c1e9a-idem");

        WorkOrder first = workOrderService.report(1L, req);
        WorkOrder second = workOrderService.report(1L, req);

        assertEquals("PENDING_QC", second.getStatus());
        assertEquals(first.getQtyDone(), second.getQtyDone());
        assertEquals(10, second.getQtyDone());
        verify(workOrderReportMapper, times(1)).insert(any(WorkOrderReport.class));
        verify(statusHistoryMapper, times(1)).insert(any(WorkOrderStatusHistory.class));
    }

    @Test
    void reportRejectsIdempotencyKeyLongerThan64() {
        WorkOrder order = order("IN_PRODUCTION");
        when(workOrderMapper.selectById(1L)).thenReturn(order);

        assertThrows(BadRequestException.class,
                () -> workOrderService.report(1L, new ReportRequest(10, 9, 1, null, "zhangsan", "k".repeat(65))));
        verify(workOrderReportMapper, never()).insert(any(WorkOrderReport.class));
    }

    @Test
    void qcPassFinishesOrder() {
        WorkOrder order = order("PENDING_QC");
        when(workOrderMapper.selectById(1L)).thenReturn(order);
        when(codeGenerator.next("QC")).thenReturn("QC-202609-0001");
        when(workOrderMapper.updateById(order)).thenReturn(1);
        when(workOrderMapper.selectCount(any())).thenReturn(1L);

        WorkOrder result = workOrderService.qc(1L, new QcRequest("PASS", null, "lisi"));

        assertEquals("FINISHED", result.getStatus());
        verify(qualityRecordMapper).insert(any(QualityRecord.class));
        WorkOrderStatusHistory history = capturedHistory();
        assertEquals("PENDING_QC", history.getFromStatus());
        assertEquals("FINISHED", history.getToStatus());
        assertEquals("lisi", history.getOperator());
    }

    @Test
    void qcFailReturnsToProduction() {
        WorkOrder order = order("PENDING_QC");
        when(workOrderMapper.selectById(1L)).thenReturn(order);
        when(codeGenerator.next("QC")).thenReturn("QC-202609-0001");
        when(workOrderMapper.updateById(order)).thenReturn(1);

        WorkOrder result = workOrderService.qc(1L, new QcRequest("FAIL", "尺寸超差", "lisi"));

        assertEquals("IN_PRODUCTION", result.getStatus());
        assertEquals("尺寸超差", capturedHistory().getRemark());
    }

    @Test
    void qcInvalidResultWritesNoRecord() {
        WorkOrder order = order("PENDING_QC");
        when(workOrderMapper.selectById(1L)).thenReturn(order);

        assertThrows(BusinessException.class,
                () -> workOrderService.qc(1L, new QcRequest("OTHER", null, "lisi")));

        verify(qualityRecordMapper, never()).insert(any(QualityRecord.class));
        verify(statusHistoryMapper, never()).insert(any(WorkOrderStatusHistory.class));
    }
}
