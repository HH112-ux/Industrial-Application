package com.zhizao.service;

import com.zhizao.common.BusinessException;
import com.zhizao.entity.WorkOrder;
import com.zhizao.enums.WorkOrderStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * 工单状态机：只允许合法状态流转，非法流转抛业务异常。
 */
@Component
public class WorkOrderStateMachine {

    private static final Map<WorkOrderStatus, Set<WorkOrderStatus>> TRANSITIONS = Map.of(
            WorkOrderStatus.PENDING, Set.of(WorkOrderStatus.IN_PRODUCTION),
            WorkOrderStatus.IN_PRODUCTION, Set.of(WorkOrderStatus.PENDING_QC),
            WorkOrderStatus.PENDING_QC, Set.of(WorkOrderStatus.FINISHED, WorkOrderStatus.IN_PRODUCTION),
            WorkOrderStatus.FINISHED, Set.of()
    );

    public void transition(WorkOrder order, WorkOrderStatus to) {
        WorkOrderStatus from = WorkOrderStatus.valueOf(order.getStatus());
        Set<WorkOrderStatus> allowed = TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new BusinessException("非法状态流转：" + from.getLabel() + " → " + to.getLabel());
        }
        order.setStatus(to.name());
        order.setUpdatedAt(LocalDateTime.now());
    }
}
