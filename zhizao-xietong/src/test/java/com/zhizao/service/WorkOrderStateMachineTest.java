package com.zhizao.service;

import com.zhizao.common.BusinessException;
import com.zhizao.entity.WorkOrder;
import com.zhizao.enums.WorkOrderStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkOrderStateMachineTest {

    private final WorkOrderStateMachine stateMachine = new WorkOrderStateMachine();

    private WorkOrder order(WorkOrderStatus status) {
        WorkOrder order = new WorkOrder();
        order.setId(1L);
        order.setStatus(status.name());
        return order;
    }

    @Test
    void legalTransitionUpdatesStatus() {
        WorkOrder order = order(WorkOrderStatus.PENDING);

        stateMachine.transition(order, WorkOrderStatus.IN_PRODUCTION);

        assertEquals("IN_PRODUCTION", order.getStatus());
    }

    @Test
    void illegalTransitionIsRejected() {
        WorkOrder order = order(WorkOrderStatus.PENDING);

        assertThrows(BusinessException.class, () -> stateMachine.transition(order, WorkOrderStatus.FINISHED));
        assertEquals("PENDING", order.getStatus());
    }
}
