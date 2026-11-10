package com.zhizao.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhizao.common.BusinessException;
import com.zhizao.entity.Material;
import com.zhizao.mapper.MaterialMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 库存扣减边界：库存不足、库存为 NULL、物料缺失都必须显式失败，不得静默封底。
 *
 * @author Caspian
 */
@ExtendWith(MockitoExtension.class)
class MaterialServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Material.class);
    }

    @Mock
    private MaterialMapper materialMapper;
    @Mock
    private CodeGenerator codeGenerator;

    private MaterialService service() {
        return new MaterialService(materialMapper, codeGenerator);
    }

    private Material material(Integer stock) {
        Material material = new Material();
        material.setMatCode("MT-001");
        material.setStockQty(stock);
        return material;
    }

    @Test
    void rejectsInsufficientStockInsteadOfClampingToZero() {
        when(materialMapper.update(any(), any())).thenReturn(0);
        when(materialMapper.selectOne(any())).thenReturn(material(3));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service().deductStock("MT-001", 5));

        assertTrue(error.getMessage().contains("库存不足"));
    }

    @Test
    void rejectsNullStockInsteadOfComputingNull() {
        when(materialMapper.update(any(), any())).thenReturn(0);
        when(materialMapper.selectOne(any())).thenReturn(material(null));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service().deductStock("MT-001", 5));

        assertTrue(error.getMessage().contains("NULL"));
    }

    @Test
    void rejectsMissingMaterial() {
        when(materialMapper.update(any(), any())).thenReturn(0);
        when(materialMapper.selectOne(any())).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service().deductStock("MT-404", 5));

        assertTrue(error.getMessage().contains("不存在"));
    }

    @Test
    void skipsFallbackQueryWhenUpdateSucceeds() {
        when(materialMapper.update(any(), any())).thenReturn(1);

        assertDoesNotThrow(() -> service().deductStock("MT-001", 5));
        verify(materialMapper, never()).selectOne(any());
    }

    @Test
    void ignoresNonPositiveQuantity() {
        assertDoesNotThrow(() -> service().deductStock("MT-001", 0));

        verifyNoInteractions(materialMapper);
    }
}
