package com.zhizao.service;

import com.zhizao.entity.FinishedGoodsInbound;
import com.zhizao.entity.WorkOrder;
import com.zhizao.mapper.FinishedGoodsInboundMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinishedGoodsInboundServiceTest {

    @Mock
    private FinishedGoodsInboundMapper inboundMapper;

    private FinishedGoodsInboundService inboundService;

    @BeforeEach
    void setUp() {
        inboundService = new FinishedGoodsInboundService(inboundMapper);
    }

    private WorkOrder finishedOrder(int qty, int qualified) {
        WorkOrder order = new WorkOrder();
        order.setId(1L);
        order.setOrderNo("WO-202611-0001");
        order.setStatus("FINISHED");
        order.setProductCode("P-100");
        order.setWorkshop("一车间");
        order.setQty(qty);
        order.setQtyQualified(qualified);
        return order;
    }

    @Test
    void generateUsesSmallerOfPlannedAndQualifiedQuantity() {
        when(inboundMapper.sumInboundQty(1L)).thenReturn(0L);

        inboundService.generateFor(finishedOrder(10, 8));

        ArgumentCaptor<FinishedGoodsInbound> captor = ArgumentCaptor.forClass(FinishedGoodsInbound.class);
        verify(inboundMapper).insert(captor.capture());
        FinishedGoodsInbound record = captor.getValue();
        assertEquals(8, record.getQty());
        assertEquals("PENDING", record.getStatus());
        assertEquals("P-100", record.getProductCode());
        assertEquals("一车间", record.getWorkshop());
        assertEquals("WO-202611-0001-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                record.getBatch());
    }

    @Test
    void generateSkipsWhenNothingLeftToInbound() {
        when(inboundMapper.sumInboundQty(1L)).thenReturn(8L);

        inboundService.generateFor(finishedOrder(10, 8));

        verify(inboundMapper, never()).insert(any(FinishedGoodsInbound.class));
    }

    @Test
    void generateIsIdempotentWhenUniqueKeyConflicts() {
        when(inboundMapper.sumInboundQty(1L)).thenReturn(0L);
        when(inboundMapper.insert(any(FinishedGoodsInbound.class))).thenThrow(new DuplicateKeyException("dup"));

        assertDoesNotThrow(() -> inboundService.generateFor(finishedOrder(10, 10)));
    }
}
