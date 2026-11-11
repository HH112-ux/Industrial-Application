package com.zhizao.service;

import com.zhizao.common.BadRequestException;
import com.zhizao.dto.ScheduleEntryView;
import com.zhizao.dto.ScheduleRunResult;
import com.zhizao.entity.Device;
import com.zhizao.entity.Plan;
import com.zhizao.entity.ProcessOperation;
import com.zhizao.entity.ScheduleEntry;
import com.zhizao.entity.WorkOrder;
import com.zhizao.enums.ScheduleConflict;
import com.zhizao.enums.ScheduleStatus;
import com.zhizao.mapper.DeviceMapper;
import com.zhizao.mapper.PlanMapper;
import com.zhizao.mapper.ProcessOperationMapper;
import com.zhizao.mapper.ScheduleEntryMapper;
import com.zhizao.mapper.WorkOrderMapper;
import com.zhizao.security.DataScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 排程边界用例：产能刚好排满、超出一点排不进、同类型竞争、空产能、重排不改在产。
 *
 * @author Caspian
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduleServiceTest {

    /** 用未来日期，避免用例在「计划开始日已过」时因运行时钟不同而失败。 */
    private static final LocalDate DAY = LocalDate.now().plusDays(1);
    private static final String TYPE = "加工设备";

    @Mock
    private WorkOrderMapper workOrderMapper;
    @Mock
    private PlanMapper planMapper;
    @Mock
    private ProcessOperationMapper processOperationMapper;
    @Mock
    private DeviceMapper deviceMapper;
    @Mock
    private ScheduleEntryMapper scheduleEntryMapper;
    @Mock
    private DataScope dataScope;

    private ScheduleService scheduleService;

    @BeforeEach
    void setUp() {
        scheduleService = newService(BigDecimal.ZERO);
        when(scheduleEntryMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void schedulesWhenCapacityIsExactlyFilled() {
        givenWorkOrders(order(101L, 1L, 5L, 8));
        givenPlans(plan(1L, DAY, DAY, "HIGH"));
        givenOperations(operation(5L, 1, TYPE, "1"));
        givenDevices(device(1L, TYPE, "RUNNING"));

        ScheduleRunResult result = scheduleService.run();

        assertEquals(1, result.scheduledWorkOrders());
        assertEquals(0, result.unscheduledWorkOrders());
        ScheduleEntry entry = result.scheduled().get(0);
        assertEquals(ScheduleStatus.SCHEDULED.name(), entry.getStatus());
        assertEquals(Long.valueOf(1L), entry.getDeviceId());
        assertEquals(DAY.atTime(8, 0), entry.getStartTime());
        assertEquals(DAY.atTime(16, 0), entry.getEndTime());
        assertEquals(0, new BigDecimal("8.00").compareTo(entry.getDurationHours()));
        assertEquals(1, entry.getQueueRank());
        assertTrue(entry.getRankBasis().contains("交期 " + DAY));
        assertEquals("STANDARD", entry.getHoursSource());
        assertNull(entry.getConflictCode());
    }

    @Test
    void rejectsWhenStandardHoursMissingEvenIfPlanHasTotalHours() {
        Plan plan = plan(1L, DAY, DAY, "HIGH");
        plan.setEstHours(new BigDecimal("40"));
        givenWorkOrders(order(101L, 1L, 5L, 8));
        givenPlans(plan);
        givenOperations(operation(5L, 1, TYPE, null));
        givenDevices(device(1L, TYPE, "RUNNING"));

        ScheduleRunResult result = scheduleService.run();

        assertEquals(0, result.scheduledWorkOrders());
        assertEquals(1, result.unscheduledWorkOrders());
        ScheduleEntry entry = result.unscheduled().get(0);
        assertEquals(ScheduleConflict.STANDARD_HOURS_MISSING.code(), entry.getConflictCode());
        assertTrue(entry.getMessage().contains("工序1"));
        assertNull(entry.getHoursSource());
    }

    @Test
    void schedulesWithEstimatedHoursOnlyWhenExplicitlyEnabled() {
        ScheduleService withEstimate = newService(BigDecimal.ONE);
        givenWorkOrders(order(101L, 1L, 5L, 8));
        givenPlans(plan(1L, DAY, DAY, "HIGH"));
        givenOperations(operation(5L, 1, TYPE, null));
        givenDevices(device(1L, TYPE, "RUNNING"));

        ScheduleRunResult result = withEstimate.run();

        assertEquals(1, result.scheduledWorkOrders());
        ScheduleEntry entry = result.scheduled().get(0);
        assertEquals(Integer.valueOf(1), entry.getEstimated());
        assertEquals("ESTIMATED", entry.getHoursSource());
        assertTrue(entry.getMessage().contains("估算工时"));
        assertEquals(0, BigDecimal.ONE.compareTo(entry.getDurationHours()));
    }

    @Test
    void rejectsWhenCapacityOverflowsByOneHour() {
        givenWorkOrders(order(101L, 1L, 5L, 9));
        givenPlans(plan(1L, DAY, DAY, "HIGH"));
        givenOperations(operation(5L, 1, TYPE, "1"));
        givenDevices(device(1L, TYPE, "RUNNING"));

        ScheduleRunResult result = scheduleService.run();

        assertEquals(0, result.scheduledWorkOrders());
        assertEquals(1, result.unscheduledWorkOrders());
        ScheduleEntry entry = result.unscheduled().get(0);
        assertEquals(ScheduleStatus.UNSCHEDULED.name(), entry.getStatus());
        assertEquals(ScheduleConflict.CAPACITY_OVERLOAD.code(), entry.getConflictCode());
        assertTrue(entry.getMessage().contains(TYPE));
        assertNull(entry.getStartTime());
    }

    @Test
    void competingWorkOrdersOnSameDeviceTypeRespectQueueOrder() {
        givenWorkOrders(order(102L, 2L, 5L, 8), order(101L, 1L, 5L, 8));
        givenPlans(plan(1L, DAY, DAY, "HIGH"), plan(2L, DAY, DAY, "LOW"));
        givenOperations(operation(5L, 1, TYPE, "1"));
        givenDevices(device(1L, TYPE, "RUNNING"));

        ScheduleRunResult result = scheduleService.run();

        assertEquals(1, result.scheduledWorkOrders());
        assertEquals(1, result.unscheduledWorkOrders());
        ScheduleEntry scheduled = result.scheduled().get(0);
        assertEquals(Long.valueOf(101L), scheduled.getWorkOrderId());
        assertEquals(1, scheduled.getQueueRank());
        assertTrue(scheduled.getRankBasis().contains("HIGH"));

        ScheduleEntry rejected = result.unscheduled().get(0);
        assertEquals(Long.valueOf(102L), rejected.getWorkOrderId());
        assertEquals(2, rejected.getQueueRank());
        assertEquals(ScheduleConflict.CAPACITY_OVERLOAD.code(), rejected.getConflictCode());
    }

    @Test
    void marksUnscheduledWhenNoCapacityData() {
        givenWorkOrders(order(101L, 1L, 5L, 8));
        givenPlans(plan(1L, DAY, DAY, "HIGH"));
        givenOperations(operation(5L, 1, TYPE, "1"));
        givenDevices();

        ScheduleRunResult result = scheduleService.run();

        assertEquals(0, result.scheduledWorkOrders());
        assertEquals(1, result.unscheduledWorkOrders());
        ScheduleEntry entry = result.unscheduled().get(0);
        assertEquals(ScheduleConflict.NO_CAPACITY.code(), entry.getConflictCode());
        assertTrue(entry.getMessage().contains(TYPE));
    }

    @Test
    void rerunKeepsInProductionEntriesAndSchedulesAroundThem() {
        WorkOrder inProduction = order(201L, 2L, 5L, 8);
        inProduction.setStatus("IN_PRODUCTION");
        ScheduleEntry existing = new ScheduleEntry();
        existing.setId(900L);
        existing.setWorkOrderId(201L);
        existing.setPlanId(2L);
        existing.setDeviceId(1L);
        existing.setDeviceType(TYPE);
        existing.setStartTime(DAY.atTime(8, 0));
        existing.setEndTime(DAY.atTime(16, 0));
        existing.setStatus(ScheduleStatus.SCHEDULED.name());
        when(scheduleEntryMapper.selectList(any())).thenReturn(List.of(existing));

        givenWorkOrders(order(101L, 1L, 5L, 8));
        givenPlans(plan(1L, DAY, DAY.plusDays(1), "HIGH"));
        givenOperations(operation(5L, 1, TYPE, "1"));
        givenDevices(device(1L, TYPE, "RUNNING"));

        ScheduleRunResult result = scheduleService.run();

        assertEquals(1, result.scheduledWorkOrders());
        ScheduleEntry entry = result.scheduled().get(0);
        assertEquals(Long.valueOf(101L), entry.getWorkOrderId());
        assertEquals(DAY.plusDays(1).atTime(8, 0), entry.getStartTime());
        assertEquals(DAY.plusDays(1).atTime(16, 0), entry.getEndTime());

        assertEquals(DAY.atTime(8, 0), existing.getStartTime());
        assertEquals(DAY.atTime(16, 0), existing.getEndTime());
        assertTrue(result.scheduled().stream().noneMatch(item -> Long.valueOf(201L).equals(item.getWorkOrderId())));
        verify(scheduleEntryMapper, times(1)).delete(any());
    }

    @Test
    void skipsOrderBatchQueryWhenOccupancyHasNoWorkOrderIds() {
        ScheduleEntry orphan = new ScheduleEntry();
        orphan.setDeviceId(1L);
        orphan.setStartTime(DAY.atTime(8, 0));
        orphan.setEndTime(DAY.atTime(9, 0));
        orphan.setStatus(ScheduleStatus.SCHEDULED.name());
        when(scheduleEntryMapper.selectList(any())).thenReturn(List.of(orphan));

        givenWorkOrders(order(101L, 1L, 5L, 8));
        givenPlans(plan(1L, DAY, DAY.plusDays(1), "HIGH"));
        givenOperations(operation(5L, 1, TYPE, "1"));
        givenDevices(device(1L, TYPE, "RUNNING"));

        assertDoesNotThrow(() -> scheduleService.run());
        verify(workOrderMapper, never()).selectBatchIds(any());
    }

    @Test
    void rerunWithSameInputsProducesIdenticalWindows() {
        givenWorkOrders(order(101L, 1L, 5L, 8));
        givenPlans(plan(1L, DAY, DAY, "HIGH"));
        givenOperations(operation(5L, 1, TYPE, "1"));
        givenDevices(device(1L, TYPE, "RUNNING"));

        ScheduleEntry first = scheduleService.run().scheduled().get(0);
        ScheduleEntry second = scheduleService.run().scheduled().get(0);

        assertEquals(first.getDeviceId(), second.getDeviceId());
        assertEquals(first.getStartTime(), second.getStartTime());
        assertEquals(first.getEndTime(), second.getEndTime());
        assertEquals(first.getQueueRank(), second.getQueueRank());
        assertEquals(first.getDurationHours(), second.getDurationHours());
    }

    @Test
    void startsNoEarlierThanRunTimeWhenPlanStartIsPast() {
        LocalDate pastStart = LocalDate.now().minusDays(10);
        LocalDate end = LocalDate.now().plusDays(2);
        givenWorkOrders(order(101L, 1L, 5L, 1));
        givenPlans(plan(1L, pastStart, end, "HIGH"));
        givenOperations(operation(5L, 1, TYPE, "1"));
        givenDevices(device(1L, TYPE, "RUNNING"));

        LocalDateTime now = LocalDateTime.now();
        ScheduleRunResult result = scheduleService.run();

        ScheduleEntry entry = result.scheduled().get(0);
        assertTrue(!entry.getStartTime().isBefore(now), "计划开始日已过时必须从运行时间起排");
        assertTrue(entry.getMessage().contains("计划开始日"));
        assertTrue(entry.getMessage().contains("已过"));
    }

    @Test
    void addsSetupHoursWhenDeviceSwitchesProduct() {
        WorkOrder first = order(101L, 1L, 5L, 1);
        first.setProductCode("P-001");
        WorkOrder second = order(102L, 1L, 5L, 1);
        second.setProductCode("P-002");
        givenWorkOrders(first, second);
        givenPlans(plan(1L, DAY, DAY, "HIGH"));
        ProcessOperation operation = operation(5L, 1, TYPE, "1");
        operation.setSetupHours(new BigDecimal("1"));
        givenOperations(operation);
        givenDevices(device(1L, TYPE, "RUNNING"));

        ScheduleRunResult result = scheduleService.run();

        assertEquals(2, result.scheduledWorkOrders());
        ScheduleEntry firstEntry = result.scheduled().get(0);
        ScheduleEntry secondEntry = result.scheduled().get(1);
        assertEquals(DAY.atTime(8, 0), firstEntry.getStartTime());
        assertEquals(DAY.atTime(9, 0), firstEntry.getEndTime());
        assertEquals(DAY.atTime(9, 0), secondEntry.getStartTime());
        assertEquals(DAY.atTime(11, 0), secondEntry.getEndTime());
        assertEquals(0, new BigDecimal("2.00").compareTo(secondEntry.getDurationHours()));
        assertTrue(secondEntry.getMessage().contains("换型 1"));
    }

    @Test
    void entriesIncludeWorkOrderAndDeviceLabels() {
        WorkOrder order = order(101L, 1L, 5L, 8);
        order.setOrderNo("WO-101");
        order.setProductCode("P-001");
        order.setProductName("主轴");
        order.setWorkshop("一车间");
        ScheduleEntry entry = new ScheduleEntry();
        entry.setId(900L);
        entry.setWorkOrderId(101L);
        entry.setPlanId(1L);
        entry.setOperationSeq(1);
        entry.setOperationName("粗加工");
        entry.setDeviceType(TYPE);
        entry.setDeviceId(1L);
        entry.setStartTime(DAY.atTime(8, 0));
        entry.setEndTime(DAY.atTime(16, 0));
        entry.setDurationHours(new BigDecimal("8.00"));
        entry.setEstimated(0);
        entry.setQueueRank(1);
        entry.setRankBasis("交期 " + DAY + "，优先级 HIGH(3)");
        entry.setStatus(ScheduleStatus.SCHEDULED.name());
        when(scheduleEntryMapper.selectList(any())).thenReturn(List.of(entry));
        Device device = device(1L, TYPE, "RUNNING");
        device.setDeviceNo("D-01");
        device.setDeviceName("加工中心 1");
        when(deviceMapper.selectBatchIds(any())).thenReturn(List.of(device));

        List<ScheduleEntryView> views = scheduleService.entries(order);

        assertEquals(1, views.size());
        ScheduleEntryView view = views.get(0);
        assertEquals("WO-101", view.orderNo());
        assertEquals("主轴", view.productName());
        assertEquals("一车间", view.workshop());
        assertEquals("D-01", view.deviceNo());
        assertEquals("加工中心 1", view.deviceName());
        assertEquals("STANDARD", view.hoursSource());
        assertEquals(DAY.atTime(8, 0), view.startTime());
    }

    @Test
    void deviceTimelineIncludesScopedDeviceOccupancy() {
        when(dataScope.workshopFilter()).thenReturn("一车间");
        Device device = device(1L, TYPE, "RUNNING");
        device.setDeviceNo("D-01");
        device.setWorkshop("一车间");
        when(deviceMapper.selectList(any())).thenReturn(List.of(device));
        when(deviceMapper.selectBatchIds(any())).thenReturn(List.of(device));
        WorkOrder order = order(101L, 1L, 5L, 8);
        order.setOrderNo("WO-101");
        when(workOrderMapper.selectBatchIds(any())).thenReturn(List.of(order));
        ScheduleEntry entry = new ScheduleEntry();
        entry.setWorkOrderId(101L);
        entry.setPlanId(1L);
        entry.setDeviceId(1L);
        entry.setStartTime(DAY.atTime(8, 0));
        entry.setEndTime(DAY.atTime(16, 0));
        entry.setStatus(ScheduleStatus.SCHEDULED.name());
        when(scheduleEntryMapper.selectList(any())).thenReturn(List.of(entry));

        List<ScheduleEntryView> views = scheduleService.deviceTimeline(DAY, DAY);

        assertEquals(1, views.size());
        assertEquals("WO-101", views.get(0).orderNo());
        assertEquals("D-01", views.get(0).deviceNo());
    }

    @Test
    void deviceTimelineReturnsNothingWhenWorkshopScopeIsEmpty() {
        when(dataScope.workshopFilter()).thenReturn("");
        when(deviceMapper.selectList(any())).thenReturn(List.of());

        assertTrue(scheduleService.deviceTimeline(DAY, DAY).isEmpty());
        verify(scheduleEntryMapper, never()).selectList(any());
    }

    @Test
    void deviceTimelineRejectsInvalidRange() {
        assertThrows(BadRequestException.class,
                () -> scheduleService.deviceTimeline(DAY, DAY.minusDays(1)));
        assertThrows(BadRequestException.class,
                () -> scheduleService.deviceTimeline(DAY, DAY.plusDays(32)));
    }

    @Test
    void rejectsInvalidScheduleConfig() {
        assertThrows(IllegalArgumentException.class, () -> new ScheduleService(
                workOrderMapper, planMapper, processOperationMapper, deviceMapper, scheduleEntryMapper,
                dataScope, BigDecimal.valueOf(8), 24, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new ScheduleService(
                workOrderMapper, planMapper, processOperationMapper, deviceMapper, scheduleEntryMapper,
                dataScope, BigDecimal.ZERO, 8, BigDecimal.ZERO));
    }

    private ScheduleService newService(BigDecimal estimateFallbackHours) {
        return new ScheduleService(workOrderMapper, planMapper, processOperationMapper,
                deviceMapper, scheduleEntryMapper, dataScope, BigDecimal.valueOf(8), 8, estimateFallbackHours);
    }

    private void givenWorkOrders(WorkOrder... orders) {
        when(workOrderMapper.selectList(any())).thenReturn(List.of(orders));
    }

    private void givenPlans(Plan... plans) {
        when(planMapper.selectBatchIds(any())).thenReturn(List.of(plans));
    }

    private void givenOperations(ProcessOperation... operations) {
        when(processOperationMapper.selectList(any())).thenReturn(List.of(operations));
    }

    private void givenDevices(Device... devices) {
        when(deviceMapper.selectList(any())).thenReturn(List.of(devices));
    }

    private WorkOrder order(long id, long planId, long processId, int qty) {
        WorkOrder order = new WorkOrder();
        order.setId(id);
        order.setPlanId(planId);
        order.setProcessId(processId);
        order.setQty(qty);
        order.setStatus("PENDING");
        return order;
    }

    private Plan plan(long id, LocalDate start, LocalDate end, String priority) {
        Plan plan = new Plan();
        plan.setId(id);
        plan.setPlanStart(start);
        plan.setPlanEnd(end);
        plan.setPriority(priority);
        return plan;
    }

    private ProcessOperation operation(long processId, int seq, String deviceType, String estHours) {
        ProcessOperation operation = new ProcessOperation();
        operation.setProcessId(processId);
        operation.setSeq(seq);
        operation.setOperationName("工序" + seq);
        operation.setDeviceType(deviceType);
        operation.setEstHours(estHours == null ? null : new BigDecimal(estHours));
        return operation;
    }

    private Device device(long id, String type, String status) {
        Device device = new Device();
        device.setId(id);
        device.setDeviceType(type);
        device.setStatus(status);
        return device;
    }
}
