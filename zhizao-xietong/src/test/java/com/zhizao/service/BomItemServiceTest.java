package com.zhizao.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhizao.common.BusinessException;
import com.zhizao.entity.BomItem;
import com.zhizao.entity.Material;
import com.zhizao.entity.Process;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BomItemServiceTest {

    @Mock
    private BomItemMapper bomItemMapper;
    @Mock
    private MaterialService materialService;
    @Mock
    private ProcessService processService;

    @InjectMocks
    private BomItemService bomItemService;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BomItem.class);
    }

    private BomItem item(String matCode) {
        BomItem item = new BomItem();
        item.setMatCode(matCode);
        item.setRequiredQty(2);
        return item;
    }

    private Material material(String matCode) {
        Material material = new Material();
        material.setMatCode(matCode);
        material.setMatName("45#圆钢");
        return material;
    }

    @Test
    void createRejectsDuplicateMaterial() {
        when(processService.requireEditable(1L)).thenReturn(new Process());
        when(materialService.getByCode("MT-001")).thenReturn(material("MT-001"));
        when(bomItemMapper.selectCount(any())).thenReturn(1L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> bomItemService.create(1L, item("MT-001")));

        assertEquals("该物料已在 BOM 中：MT-001", error.getMessage());
        verify(bomItemMapper, never()).insert(any(BomItem.class));
    }

    @Test
    void createRejectsPublishedProcess() {
        when(processService.requireEditable(1L))
                .thenThrow(new BusinessException("已发布工艺不可编辑，请新建版本"));

        assertThrows(BusinessException.class, () -> bomItemService.create(1L, item("MT-001")));
        verify(bomItemMapper, never()).insert(any(BomItem.class));
    }

    @Test
    void listByProcessFiltersByProcessIdOnly() {
        when(bomItemMapper.selectList(any())).thenReturn(List.of(item("MT-001")));

        bomItemService.listByProcess(1L);

        ArgumentCaptor<LambdaQueryWrapper<BomItem>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(bomItemMapper).selectList(captor.capture());
        LambdaQueryWrapper<BomItem> wrapper = captor.getValue();
        assertEquals("(process_id = #{ew.paramNameValuePairs.MPGENVAL1})", wrapper.getSqlSegment());
        assertEquals(List.of(1L), wrapper.getParamNameValuePairs().values().stream().toList());
    }
}
