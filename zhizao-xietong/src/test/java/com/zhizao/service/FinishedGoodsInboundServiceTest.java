package com.zhizao.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhizao.common.BadRequestException;
import com.zhizao.common.BusinessException;
import com.zhizao.entity.FinishedGoodsInbound;
import com.zhizao.entity.WorkOrder;
import com.zhizao.mapper.FinishedGoodsInboundMapper;
import com.zhizao.mapper.FinishedGoodsStockMapper;
import com.zhizao.security.DataScope;
import com.zhizao.security.UserPrincipal;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinishedGoodsInboundServiceTest {

    @Mock
    private FinishedGoodsInboundMapper inboundMapper;

    @Mock
    private FinishedGoodsStockMapper stockMapper;

    private FinishedGoodsInboundService inboundService;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                FinishedGoodsInbound.class);
    }

    @BeforeEach
    void setUp() {
        inboundService = new FinishedGoodsInboundService(inboundMapper, stockMapper, new DataScope());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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

    @Test
    void listTreatsEmptyWorkshopScopeAsNoResult() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new UserPrincipal("worker", "USER", ""), null, List.of()));
        when(inboundMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = inboundService.list(1, 10, null, null, null);

        assertEquals(0, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<FinishedGoodsInbound>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(inboundMapper).selectPage(any(), captor.capture());
        AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) captor.getValue();
        wrapper.getSqlSegment();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(""),
                "未分配车间时应以空串作为车间条件，返回空而不是放开全量");
    }

    @Test
    void cancelRejectsBlankReason() {
        assertThrows(BadRequestException.class, () -> inboundService.cancel(1L, "  ", "zhangsan"));

        verify(inboundMapper, never()).update(any(), any());
    }

    @Test
    void cancelRejectsWhenStatusAlreadyChanged() {
        when(inboundMapper.selectById(1L)).thenReturn(pendingInbound());
        when(inboundMapper.update(isNull(), any())).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> inboundService.cancel(1L, "客户取消", "zhangsan"));

        assertEquals(409, error.getCode());
    }

    @Test
    void cancelMarksPendingInboundCancelled() {
        FinishedGoodsInbound cancelled = pendingInbound();
        cancelled.setStatus("CANCELLED");
        cancelled.setCancelReason("客户取消");
        cancelled.setOperator("zhangsan");
        when(inboundMapper.selectById(1L)).thenReturn(pendingInbound(), cancelled);
        when(inboundMapper.update(isNull(), any())).thenReturn(1);

        FinishedGoodsInbound result = inboundService.cancel(1L, "客户取消", "zhangsan");

        assertEquals("CANCELLED", result.getStatus());
        assertEquals("客户取消", result.getCancelReason());
        assertEquals("zhangsan", result.getOperator());
    }

    @Test
    void confirmAccumulatesStockAfterConditionalUpdate() {
        FinishedGoodsInbound confirmed = pendingInbound();
        confirmed.setStatus("INBOUND");
        when(inboundMapper.selectById(1L)).thenReturn(pendingInbound(), confirmed);
        when(inboundMapper.confirm(1L, "B-9", "zhangsan")).thenReturn(1);

        FinishedGoodsInbound result = inboundService.confirm(1L, "B-9", "zhangsan");

        assertEquals("INBOUND", result.getStatus());
        verify(stockMapper).accumulate("P-100", "一车间", 8);
    }

    @Test
    void confirmReportsConflictWhenAlreadyHandled() {
        FinishedGoodsInbound cancelled = pendingInbound();
        cancelled.setStatus("CANCELLED");
        when(inboundMapper.selectById(1L)).thenReturn(pendingInbound());
        when(inboundMapper.selectByIdForUpdate(1L)).thenReturn(cancelled);
        when(inboundMapper.confirm(1L, "", "zhangsan")).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> inboundService.confirm(1L, null, "zhangsan"));

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("已确认或已取消"));
        verify(stockMapper, never()).accumulate(any(), any(), anyInt());
    }

    @Test
    void confirmReportsConflictWhenQuantityExceedsQualified() {
        when(inboundMapper.selectById(1L)).thenReturn(pendingInbound());
        when(inboundMapper.selectByIdForUpdate(1L)).thenReturn(pendingInbound());
        when(inboundMapper.confirm(1L, "", "zhangsan")).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> inboundService.confirm(1L, null, "zhangsan"));

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("超过"));
        verify(stockMapper, never()).accumulate(any(), any(), anyInt());
    }

    private FinishedGoodsInbound pendingInbound() {
        FinishedGoodsInbound record = new FinishedGoodsInbound();
        record.setId(1L);
        record.setProductCode("P-100");
        record.setQty(8);
        record.setWorkshop("一车间");
        record.setStatus("PENDING");
        return record;
    }
}
