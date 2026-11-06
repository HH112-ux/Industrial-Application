package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.dto.ScheduleRunResult;
import com.zhizao.entity.Device;
import com.zhizao.entity.Plan;
import com.zhizao.entity.ProcessOperation;
import com.zhizao.entity.ScheduleEntry;
import com.zhizao.entity.WorkOrder;
import com.zhizao.enums.DeviceStatus;
import com.zhizao.enums.Priority;
import com.zhizao.enums.ScheduleConflict;
import com.zhizao.enums.ScheduleStatus;
import com.zhizao.enums.WorkOrderStatus;
import com.zhizao.mapper.DeviceMapper;
import com.zhizao.mapper.PlanMapper;
import com.zhizao.mapper.ProcessOperationMapper;
import com.zhizao.mapper.ScheduleEntryMapper;
import com.zhizao.mapper.WorkOrderMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

/**
 * 有限产能排程：按交期、优先级排序后，为每张「待排」工单的工序逐个寻找设备时间窗。
 *
 * <p>约束与边界：</p>
 * <ul>
 *   <li>不做 APS、不引入求解器：贪心落位，落不下就明确返回业务码与原因，不静默挪期。</li>
 *   <li>只重排「待开工」（{@code PENDING}）工单；在产、待质检与已完工工单的排程结果保留，
 *       既作为已占用产能，也作为追溯依据。</li>
 *   <li>同一批工单与同一份产能数据重复运行，排序、设备选择与时间窗完全一致。</li>
 *   <li>产能来自运行中（{@code RUNNING}）的设备，每台设备每日可用工时由
 *       {@code schedule.daily-hours} 与 {@code schedule.day-start-hour} 决定。</li>
 * </ul>
 *
 * @author Caspian
 */
@Service
public class ScheduleService {

    private static final BigDecimal DEFAULT_OPERATION_HOURS = BigDecimal.ONE;

    private final WorkOrderMapper workOrderMapper;
    private final PlanMapper planMapper;
    private final ProcessOperationMapper processOperationMapper;
    private final DeviceMapper deviceMapper;
    private final ScheduleEntryMapper scheduleEntryMapper;
    private final BigDecimal dailyHours;
    private final int dayStartHour;

    /**
     * 构造排程服务。
     *
     * @param workOrderMapper        工单数据访问
     * @param planMapper             计划数据访问
     * @param processOperationMapper 工序数据访问
     * @param deviceMapper           设备数据访问
     * @param scheduleEntryMapper    排程结果数据访问
     * @param dailyHours             单台设备每日可用工时
     * @param dayStartHour           每日开工整点，24 小时制
     */
    public ScheduleService(WorkOrderMapper workOrderMapper, PlanMapper planMapper,
                           ProcessOperationMapper processOperationMapper, DeviceMapper deviceMapper,
                           ScheduleEntryMapper scheduleEntryMapper,
                           @Value("${schedule.daily-hours:8}") BigDecimal dailyHours,
                           @Value("${schedule.day-start-hour:8}") int dayStartHour) {
        this.workOrderMapper = workOrderMapper;
        this.planMapper = planMapper;
        this.processOperationMapper = processOperationMapper;
        this.deviceMapper = deviceMapper;
        this.scheduleEntryMapper = scheduleEntryMapper;
        this.dailyHours = dailyHours;
        this.dayStartHour = dayStartHour;
    }

    /**
     * 重算全部「待开工」工单的排程。
     *
     * <p>先删除待排工单的旧结果，再按交期升序、优先级降序、工单主键升序依次落位。
     * 在产与已完工工单的结果不删除，并作为已占用产能参与落位。</p>
     *
     * @return 本次运行的成功与冲突明细
     */
    @Transactional
    public ScheduleRunResult run() {
        List<WorkOrder> pending = new ArrayList<>(workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getStatus, WorkOrderStatus.PENDING.name())
                .orderByAsc(WorkOrder::getId)));
        LocalDateTime runAt = LocalDateTime.now();
        if (pending.isEmpty()) {
            return new ScheduleRunResult(runAt, 0, 0, List.of(), List.of());
        }

        List<Long> pendingIds = pending.stream().map(WorkOrder::getId).toList();
        scheduleEntryMapper.delete(new LambdaQueryWrapper<ScheduleEntry>()
                .in(ScheduleEntry::getWorkOrderId, pendingIds));

        Map<Long, Plan> plans = loadPlans(pending);
        Map<Long, List<ProcessOperation>> operations = loadOperations(pending);
        List<Device> devices = deviceMapper.selectList(null);
        Map<Long, Device> deviceById = devices.stream()
                .collect(toMap(Device::getId, device -> device, (a, b) -> a, LinkedHashMap::new));
        Map<String, List<Device>> availableByType = devices.stream()
                .filter(device -> DeviceStatus.RUNNING.name().equals(device.getStatus()))
                .filter(device -> StringUtils.hasText(device.getDeviceType()))
                .collect(groupingBy(device -> device.getDeviceType().trim(), LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        availableByType.values().forEach(list -> list.sort(Comparator.comparing(Device::getId)));
        Map<Long, List<Interval>> reservations = loadReservations();

        pending.sort(queueOrder(plans));
        List<ScheduleEntry> scheduled = new ArrayList<>();
        List<ScheduleEntry> unscheduled = new ArrayList<>();
        int scheduledOrders = 0;
        for (int index = 0; index < pending.size(); index++) {
            WorkOrder order = pending.get(index);
            Plan plan = plans.get(order.getPlanId());
            int queueRank = index + 1;
            String rankBasis = rankBasis(plan);
            Outcome outcome = scheduleOrder(order, plan, operations.get(order.getProcessId()),
                    availableByType, deviceById, reservations, queueRank, rankBasis, runAt);
            if (outcome.conflict() != null) {
                unscheduled.add(outcome.conflict());
            } else {
                scheduled.addAll(outcome.entries());
                reservations = outcome.reservations();
                scheduledOrders++;
            }
        }
        scheduled.forEach(scheduleEntryMapper::insert);
        unscheduled.forEach(scheduleEntryMapper::insert);
        return new ScheduleRunResult(runAt, scheduledOrders, unscheduled.size(), scheduled, unscheduled);
    }

    /**
     * 查询某张工单的排程结果，用于回答「为什么排在这个时间」。
     *
     * @param workOrderId 工单主键
     * @return 按队列位置与工序顺序排列的排程记录
     */
    public List<ScheduleEntry> entries(Long workOrderId) {
        return scheduleEntryMapper.selectList(new LambdaQueryWrapper<ScheduleEntry>()
                .eq(ScheduleEntry::getWorkOrderId, workOrderId)
                .orderByAsc(ScheduleEntry::getQueueRank)
                .orderByAsc(ScheduleEntry::getOperationSeq));
    }

    /**
     * 为一张工单的全部工序落位；任一工序失败则整单不落位，避免留下半截排程。
     */
    private Outcome scheduleOrder(WorkOrder order, Plan plan, List<ProcessOperation> operations,
                                  Map<String, List<Device>> availableByType, Map<Long, Device> deviceById,
                                  Map<Long, List<Interval>> reservations, int queueRank,
                                  String rankBasis, LocalDateTime runAt) {
        if (plan == null) {
            return Outcome.conflict(conflict(order, null, null, queueRank, rankBasis,
                    ScheduleConflict.PLAN_MISSING, runAt));
        }
        if (order.getProcessId() == null) {
            return Outcome.conflict(conflict(order, plan, null, queueRank, rankBasis,
                    ScheduleConflict.PROCESS_MISSING, runAt));
        }
        if (operations == null || operations.isEmpty()) {
            return Outcome.conflict(conflict(order, plan, null, queueRank, rankBasis,
                    ScheduleConflict.OPERATION_MISSING, runAt));
        }
        if (plan.getPlanStart() == null || plan.getPlanEnd() == null) {
            return Outcome.conflict(conflict(order, plan, null, queueRank, rankBasis,
                    ScheduleConflict.DUE_DATE_MISSING, runAt));
        }
        if (order.getQty() == null || order.getQty() <= 0) {
            return Outcome.conflict(conflict(order, plan, null, queueRank, rankBasis,
                    ScheduleConflict.DURATION_INVALID, runAt));
        }

        Map<Long, List<Interval>> trial = copyReservations(reservations);
        List<ScheduleEntry> entries = new ArrayList<>();
        LocalDateTime earliest = plan.getPlanStart().atStartOfDay().plusHours(dayStartHour);
        Long preferredDeviceId = order.getDeviceId() != null ? order.getDeviceId() : plan.getRequiredDeviceId();

        for (ProcessOperation operation : operations) {
            String deviceType = resolveDeviceType(operation, plan, deviceById);
            if (!StringUtils.hasText(deviceType)) {
                return Outcome.conflict(conflict(order, plan, operation, queueRank, rankBasis,
                        ScheduleConflict.DEVICE_TYPE_MISSING, runAt));
            }
            List<Device> candidates = availableByType.getOrDefault(deviceType, List.of());
            if (candidates.isEmpty()) {
                return Outcome.conflict(conflict(order, plan, operation, queueRank, rankBasis,
                        ScheduleConflict.NO_CAPACITY, runAt, deviceType));
            }
            WorkDuration duration = resolveDuration(operation, plan, operations.size(), order.getQty());
            if (duration.hours().signum() <= 0) {
                return Outcome.conflict(conflict(order, plan, operation, queueRank, rankBasis,
                        ScheduleConflict.DURATION_INVALID, runAt));
            }
            if (earliest.toLocalDate().isAfter(plan.getPlanEnd())) {
                return Outcome.conflict(conflict(order, plan, operation, queueRank, rankBasis,
                        ScheduleConflict.DUE_DATE_MISSED, runAt, earliest, plan.getPlanEnd()));
            }

            Placement placement = place(candidates, preferredDeviceId, deviceType, earliest,
                    plan.getPlanEnd(), trial, duration.hours());
            if (placement == null) {
                return Outcome.conflict(conflict(order, plan, operation, queueRank, rankBasis,
                        ScheduleConflict.CAPACITY_OVERLOAD, runAt,
                        deviceType, earliest.toLocalDate(), plan.getPlanEnd()));
            }
            addInterval(trial, placement.device().getId(),
                    new Interval(placement.start(), placement.end()));
            earliest = placement.end();

            ScheduleEntry entry = baseEntry(order, plan, queueRank, rankBasis, runAt);
            entry.setOperationSeq(operation.getSeq());
            entry.setOperationName(operation.getOperationName());
            entry.setDeviceType(deviceType);
            entry.setDeviceId(placement.device().getId());
            entry.setStartTime(placement.start());
            entry.setEndTime(placement.end());
            entry.setDurationHours(duration.hours());
            entry.setEstimated(duration.estimated() ? 1 : 0);
            entry.setStatus(ScheduleStatus.SCHEDULED.name());
            entry.setMessage("设备类型「" + deviceType + "」第 " + queueRank + " 位落位");
            entries.add(entry);
        }
        return Outcome.success(entries, trial);
    }

    /**
     * 在指定设备类型中挑选完成时间最早的设备；同等完成时间取设备主键较小者。
     */
    private Placement place(List<Device> candidates, Long preferredDeviceId, String deviceType,
                            LocalDateTime earliest, LocalDate deadline,
                            Map<Long, List<Interval>> reservations, BigDecimal hours) {
        List<Device> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator
                .comparing((Device device) -> !device.getId().equals(preferredDeviceId))
                .thenComparing(Device::getId));
        Placement best = null;
        for (Device device : ordered) {
            Placement candidate = simulate(device, earliest, deadline,
                    reservations.getOrDefault(device.getId(), List.of()), hours);
            if (candidate == null) {
                continue;
            }
            if (best == null || candidate.end().isBefore(best.end())
                    || (candidate.end().isEqual(best.end()) && candidate.device().getId() < best.device().getId())) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * 模拟单台设备上的落位：允许跨天、允许跨过已被占用的时间窗，累计有效工时。
     */
    private Placement simulate(Device device, LocalDateTime earliest, LocalDate deadline,
                               List<Interval> busy, BigDecimal hours) {
        long remainingSeconds = hours.multiply(BigDecimal.valueOf(3600))
                .setScale(0, RoundingMode.HALF_UP).longValue();
        long dailySeconds = dailyHours.multiply(BigDecimal.valueOf(3600))
                .setScale(0, RoundingMode.HALF_UP).longValue();
        LocalDateTime start = null;
        LocalDateTime cursor = earliest;
        while (remainingSeconds > 0) {
            LocalDate date = cursor.toLocalDate();
            if (date.isAfter(deadline)) {
                return null;
            }
            LocalDateTime dayStart = date.atStartOfDay().plusHours(dayStartHour);
            LocalDateTime dayEnd = dayStart.plusSeconds(dailySeconds);
            if (cursor.isBefore(dayStart)) {
                cursor = dayStart;
            }
            if (!cursor.isBefore(dayEnd)) {
                cursor = nextDayStart(date);
                continue;
            }
            LocalDateTime scan = cursor;
            for (Interval interval : busy) {
                if (!interval.end().isAfter(scan)) {
                    continue;
                }
                if (!interval.start().isBefore(dayEnd)) {
                    break;
                }
                if (interval.start().isAfter(scan)) {
                    long free = Duration.between(scan, interval.start()).getSeconds();
                    long take = Math.min(free, remainingSeconds);
                    if (take > 0 && start == null) {
                        start = scan;
                    }
                    remainingSeconds -= take;
                    scan = scan.plusSeconds(take);
                    if (remainingSeconds <= 0) {
                        return new Placement(device, start, scan);
                    }
                }
                if (interval.end().isAfter(scan)) {
                    scan = interval.end();
                }
                if (!scan.isBefore(dayEnd)) {
                    break;
                }
            }
            if (remainingSeconds > 0 && scan.isBefore(dayEnd)) {
                long free = Duration.between(scan, dayEnd).getSeconds();
                long take = Math.min(free, remainingSeconds);
                if (take > 0 && start == null) {
                    start = scan;
                }
                remainingSeconds -= take;
                scan = scan.plusSeconds(take);
                if (remainingSeconds <= 0) {
                    return new Placement(device, start, scan);
                }
            }
            cursor = nextDayStart(date);
        }
        return null;
    }

    private LocalDateTime nextDayStart(LocalDate date) {
        return date.plusDays(1).atStartOfDay().plusHours(dayStartHour);
    }

    private WorkDuration resolveDuration(ProcessOperation operation, Plan plan, int operationCount, int qty) {
        BigDecimal unitHours = operation.getEstHours();
        if (unitHours != null && unitHours.signum() > 0) {
            return new WorkDuration(unitHours.multiply(BigDecimal.valueOf(qty))
                    .setScale(2, RoundingMode.HALF_UP), false);
        }
        if (operationCount == 1 && plan.getEstHours() != null && plan.getEstHours().signum() > 0) {
            return new WorkDuration(plan.getEstHours(), false);
        }
        return new WorkDuration(DEFAULT_OPERATION_HOURS, true);
    }

    private String resolveDeviceType(ProcessOperation operation, Plan plan, Map<Long, Device> deviceById) {
        if (StringUtils.hasText(operation.getDeviceType())) {
            return operation.getDeviceType().trim();
        }
        Long referenceId = plan.getRequiredDeviceId();
        Device reference = referenceId == null ? null : deviceById.get(referenceId);
        if (reference != null && StringUtils.hasText(reference.getDeviceType())) {
            return reference.getDeviceType().trim();
        }
        return null;
    }

    private Map<Long, Plan> loadPlans(List<WorkOrder> orders) {
        List<Long> planIds = orders.stream().map(WorkOrder::getPlanId)
                .filter(Objects::nonNull).distinct().toList();
        if (planIds.isEmpty()) {
            return Map.of();
        }
        return planMapper.selectBatchIds(planIds).stream()
                .collect(toMap(Plan::getId, plan -> plan, (a, b) -> a, LinkedHashMap::new));
    }

    private Map<Long, List<ProcessOperation>> loadOperations(List<WorkOrder> orders) {
        List<Long> processIds = orders.stream().map(WorkOrder::getProcessId)
                .filter(Objects::nonNull).distinct().toList();
        if (processIds.isEmpty()) {
            return Map.of();
        }
        List<ProcessOperation> operations = processOperationMapper.selectList(
                new LambdaQueryWrapper<ProcessOperation>()
                        .in(ProcessOperation::getProcessId, processIds)
                        .orderByAsc(ProcessOperation::getProcessId)
                        .orderByAsc(ProcessOperation::getSeq));
        return operations.stream().collect(groupingBy(ProcessOperation::getProcessId,
                LinkedHashMap::new, java.util.stream.Collectors.toList()));
    }

    /**
     * 加载已占用产能：只取 SCHEDULED 结果。待排工单的旧结果已在调用前删除，
     * 因此这里剩下的都是在产/待质检/已完工工单的记录。
     */
    private Map<Long, List<Interval>> loadReservations() {
        List<ScheduleEntry> entries = scheduleEntryMapper.selectList(new LambdaQueryWrapper<ScheduleEntry>()
                .eq(ScheduleEntry::getStatus, ScheduleStatus.SCHEDULED.name()));
        Map<Long, List<Interval>> reservations = new HashMap<>();
        for (ScheduleEntry entry : entries) {
            if (entry.getDeviceId() == null || entry.getStartTime() == null || entry.getEndTime() == null) {
                continue;
            }
            reservations.computeIfAbsent(entry.getDeviceId(), key -> new ArrayList<>())
                    .add(new Interval(entry.getStartTime(), entry.getEndTime()));
        }
        reservations.values().forEach(list ->
                list.sort(Comparator.comparing(Interval::start)));
        return reservations;
    }

    private Comparator<WorkOrder> queueOrder(Map<Long, Plan> plans) {
        return Comparator
                .comparing((WorkOrder order) -> planEnd(plans.get(order.getPlanId())),
                        Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
                .thenComparingInt(order -> -priorityWeight(plans.get(order.getPlanId())))
                .thenComparing(WorkOrder::getId);
    }

    private LocalDate planEnd(Plan plan) {
        return plan == null ? null : plan.getPlanEnd();
    }

    private int priorityWeight(Plan plan) {
        if (plan == null || !StringUtils.hasText(plan.getPriority())) {
            return Priority.MEDIUM.getWeight();
        }
        try {
            return Priority.valueOf(plan.getPriority()).getWeight();
        } catch (IllegalArgumentException e) {
            return Priority.MEDIUM.getWeight();
        }
    }

    private String rankBasis(Plan plan) {
        if (plan == null) {
            return "计划缺失";
        }
        String due = plan.getPlanEnd() == null ? "无交期" : plan.getPlanEnd().toString();
        String priority = StringUtils.hasText(plan.getPriority()) ? plan.getPriority() : "MEDIUM";
        return "交期 " + due + "，优先级 " + priority + "(" + priorityWeight(plan) + ")";
    }

    private ScheduleEntry baseEntry(WorkOrder order, Plan plan, int queueRank,
                                    String rankBasis, LocalDateTime runAt) {
        ScheduleEntry entry = new ScheduleEntry();
        entry.setWorkOrderId(order.getId());
        entry.setPlanId(order.getPlanId());
        entry.setQueueRank(queueRank);
        entry.setRankBasis(rankBasis);
        entry.setCreatedAt(runAt);
        return entry;
    }

    private ScheduleEntry conflict(WorkOrder order, Plan plan, ProcessOperation operation,
                                   int queueRank, String rankBasis, ScheduleConflict conflict,
                                   LocalDateTime runAt, Object... args) {
        ScheduleEntry entry = baseEntry(order, plan, queueRank, rankBasis, runAt);
        if (operation != null) {
            entry.setOperationSeq(operation.getSeq());
            entry.setOperationName(operation.getOperationName());
            entry.setDeviceType(operation.getDeviceType());
        }
        entry.setStatus(ScheduleStatus.UNSCHEDULED.name());
        entry.setConflictCode(conflict.code());
        entry.setMessage(conflict.message(args));
        return entry;
    }

    private Map<Long, List<Interval>> copyReservations(Map<Long, List<Interval>> source) {
        Map<Long, List<Interval>> copy = new HashMap<>();
        source.forEach((deviceId, intervals) -> copy.put(deviceId, new ArrayList<>(intervals)));
        return copy;
    }

    private void addInterval(Map<Long, List<Interval>> reservations, Long deviceId, Interval interval) {
        List<Interval> intervals = reservations.computeIfAbsent(deviceId, key -> new ArrayList<>());
        intervals.add(interval);
        intervals.sort(Comparator.comparing(Interval::start));
    }

    private record Interval(LocalDateTime start, LocalDateTime end) {
    }

    private record Placement(Device device, LocalDateTime start, LocalDateTime end) {
    }

    private record WorkDuration(BigDecimal hours, boolean estimated) {
    }

    private record Outcome(List<ScheduleEntry> entries, ScheduleEntry conflict,
                           Map<Long, List<Interval>> reservations) {

        static Outcome success(List<ScheduleEntry> entries, Map<Long, List<Interval>> reservations) {
            return new Outcome(entries, null, reservations);
        }

        static Outcome conflict(ScheduleEntry conflict) {
            return new Outcome(List.of(), conflict, Map.of());
        }
    }
}
