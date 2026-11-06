package com.zhizao.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhizao.entity.BomItem;
import com.zhizao.entity.Material;
import com.zhizao.entity.WorkOrder;
import com.zhizao.mapper.BomItemMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KitCheckServiceTest {

    @Mock
    private BomItemMapper bomItemMapper;

    @Mock
    private MaterialService materialService;

    @InjectMocks
    private KitCheckService kitCheckService;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BomItem.class);
    }

    private WorkOrder orderWithProcess(int qty) {
        WorkOrder order = new WorkOrder();
        order.setProcessId(1L);
        order.setQty(qty);
        return order;
    }

    private BomItem bomItem(String code, int qty) {
        BomItem item = new BomItem();
        item.setMatCode(code);
        item.setRequiredQty(qty);
        return item;
    }

    private Material material(String code, String name, int stock) {
        Material m = new Material();
        m.setMatCode(code);
        m.setMatName(name);
        m.setStockQty(stock);
        return m;
    }

    @Test
    void passWhenNoProcess() {
        WorkOrder order = new WorkOrder();
        order.setProcessId(null);

        KitCheckResult result = kitCheckService.check(order);

        assertTrue(result.passed());
        assertTrue(result.shortages().isEmpty());
    }

    @Test
    void passWhenEmptyBom() {
        when(bomItemMapper.selectList(any())).thenReturn(List.of());

        KitCheckResult result = kitCheckService.check(orderWithProcess(10));

        assertTrue(result.passed());
    }

    @Test
    void passWhenSufficient() {
        when(bomItemMapper.selectList(any())).thenReturn(List.of(bomItem("MT-001", 1)));
        when(materialService.listByCodes(any())).thenReturn(Map.of("MT-001", material("MT-001", "45#圆钢", 20)));

        KitCheckResult result = kitCheckService.check(orderWithProcess(10));

        assertTrue(result.passed());
        assertTrue(result.shortages().isEmpty());
    }

    @Test
    void shortageWhenInsufficient() {
        when(bomItemMapper.selectList(any())).thenReturn(List.of(bomItem("MT-001", 1)));
        when(materialService.listByCodes(any())).thenReturn(Map.of("MT-001", material("MT-001", "45#圆钢", 5)));

        KitCheckResult result = kitCheckService.check(orderWithProcess(10));

        assertFalse(result.passed());
        assertEquals(1, result.shortages().size());
        KitCheckResult.ShortageItem s = result.shortages().get(0);
        assertEquals("MT-001", s.matCode());
        assertEquals(10, s.need());
        assertEquals(5, s.stock());
    }

    @Test
    void shortageWhenMaterialMissing() {
        when(bomItemMapper.selectList(any())).thenReturn(List.of(bomItem("MT-999", 2)));
        when(materialService.listByCodes(any())).thenReturn(Map.of());

        KitCheckResult result = kitCheckService.check(orderWithProcess(3));

        assertFalse(result.passed());
        KitCheckResult.ShortageItem s = result.shortages().get(0);
        assertEquals("MT-999", s.matName());
        assertEquals(6, s.need());
        assertEquals(0, s.stock());
    }

    @Test
    void queryUsesOrderProcessIdOnly() {
        when(bomItemMapper.selectList(any())).thenReturn(List.of());

        kitCheckService.check(orderWithProcess(10));

        ArgumentCaptor<LambdaQueryWrapper<BomItem>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(bomItemMapper).selectList(captor.capture());
        LambdaQueryWrapper<BomItem> wrapper = captor.getValue();
        assertEquals("(process_id = #{ew.paramNameValuePairs.MPGENVAL1})", wrapper.getSqlSegment());
        assertEquals(List.of(1L), wrapper.getParamNameValuePairs().values().stream().toList());
    }

    @Test
    void newVersionDoesNotChangeInFlightKitResult() {
        when(bomItemMapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<BomItem> wrapper = invocation.getArgument(0);
            wrapper.getSqlSegment();
            Long processId = (Long) wrapper.getParamNameValuePairs().values().iterator().next();
            return processId == 1L
                    ? List.of(bomItem("MT-001", 1))
                    : List.of(bomItem("MT-002", 5));
        });
        when(materialService.listByCodes(any()))
                .thenReturn(Map.of("MT-001", material("MT-001", "45#圆钢", 20)))
                .thenReturn(Map.of("MT-001", material("MT-001", "45#圆钢", 20)))
                .thenReturn(Map.of("MT-002", material("MT-002", "轴承", 0)));

        WorkOrder inFlight = orderWithProcess(10);
        KitCheckResult before = kitCheckService.check(inFlight);
        KitCheckResult after = kitCheckService.check(inFlight);

        assertTrue(before.passed());
        assertTrue(after.passed());
        assertEquals(before.shortages(), after.shortages());

        WorkOrder newVersionOrder = new WorkOrder();
        newVersionOrder.setProcessId(2L);
        newVersionOrder.setQty(10);
        assertFalse(kitCheckService.check(newVersionOrder).passed());
    }
}
