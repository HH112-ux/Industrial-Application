package com.zhizao.service;

import com.zhizao.common.BusinessException;
import com.zhizao.entity.BomItem;
import com.zhizao.entity.Process;
import com.zhizao.entity.ProcessOperation;
import com.zhizao.mapper.BomItemMapper;
import com.zhizao.mapper.ProcessMapper;
import com.zhizao.mapper.ProcessOperationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessServiceTest {

    @Mock
    private ProcessMapper processMapper;
    @Mock
    private BomItemMapper bomItemMapper;
    @Mock
    private ProcessOperationMapper processOperationMapper;
    @Mock
    private CodeGenerator codeGenerator;

    @InjectMocks
    private ProcessService processService;

    private Process process(String status, int version) {
        Process process = new Process();
        process.setId(1L);
        process.setProcessNo("PR-202611-0001");
        process.setVersion(version);
        process.setProcessName("轴类加工工艺");
        process.setApprovalStatus(status);
        return process;
    }

    @Test
    void submitRejectsWhenOperationsAndBomMissing() {
        when(processMapper.selectById(1L)).thenReturn(process("DRAFT", 1));
        when(processOperationMapper.selectCount(any())).thenReturn(0L);
        when(bomItemMapper.selectCount(any())).thenReturn(0L);

        BusinessException error = assertThrows(BusinessException.class, () -> processService.submit(1L));

        assertTrue(error.getMessage().contains("工序"), error.getMessage());
        assertTrue(error.getMessage().contains("BOM 物料"), error.getMessage());
    }

    @Test
    void submitRejectsWhenOnlyOperationsPresent() {
        when(processMapper.selectById(1L)).thenReturn(process("DRAFT", 1));
        when(processOperationMapper.selectCount(any())).thenReturn(2L);
        when(bomItemMapper.selectCount(any())).thenReturn(0L);

        BusinessException error = assertThrows(BusinessException.class, () -> processService.submit(1L));

        assertEquals("提交审批前请先添加BOM 物料", error.getMessage());
    }

    @Test
    void submitRejectsWhenOnlyBomPresent() {
        when(processMapper.selectById(1L)).thenReturn(process("DRAFT", 1));
        when(processOperationMapper.selectCount(any())).thenReturn(0L);
        when(bomItemMapper.selectCount(any())).thenReturn(1L);

        BusinessException error = assertThrows(BusinessException.class, () -> processService.submit(1L));

        assertEquals("提交审批前请先添加工序", error.getMessage());
    }

    @Test
    void submitSucceedsWhenOperationsAndBomPresent() {
        Process draft = process("DRAFT", 1);
        when(processMapper.selectById(1L)).thenReturn(draft);
        when(processOperationMapper.selectCount(any())).thenReturn(2L);
        when(bomItemMapper.selectCount(any())).thenReturn(1L);

        Process result = processService.submit(1L);

        assertEquals("PENDING", result.getApprovalStatus());
        verify(processMapper).updateById(draft);
    }

    @Test
    void updateRejectsPublishedProcess() {
        when(processMapper.selectById(1L)).thenReturn(process("PUBLISHED", 1));
        Process change = process("DRAFT", 1);

        assertThrows(BusinessException.class, () -> processService.update(change));

        verify(processMapper, never()).updateById(any(Process.class));
    }

    @Test
    void createVersionCopiesOperationsAndBomAsNewDraft() {
        Process source = process("PUBLISHED", 1);
        when(processMapper.selectById(1L)).thenReturn(source);
        when(processMapper.selectMaxVersionIncludingDeleted("PR-202611-0001")).thenReturn(1);
        doAnswer(invocation -> {
            Process copy = invocation.getArgument(0);
            copy.setId(2L);
            return 1;
        }).when(processMapper).insert(any(Process.class));

        ProcessOperation op = new ProcessOperation();
        op.setSeq(1);
        op.setOperationName("粗车");
        op.setDeviceType("CNC");
        op.setEstHours(new BigDecimal("0.50"));
        when(processOperationMapper.selectList(any())).thenReturn(List.of(op));

        BomItem bom = new BomItem();
        bom.setMatCode("MT-001");
        bom.setRequiredQty(2);
        when(bomItemMapper.selectList(any())).thenReturn(List.of(bom));

        Process copy = processService.createVersion(1L);

        assertEquals(Long.valueOf(2L), copy.getId());
        assertEquals(Integer.valueOf(1), source.getVersion());
        assertEquals(Integer.valueOf(2), copy.getVersion());
        assertEquals("DRAFT", copy.getApprovalStatus());
        assertEquals("PR-202611-0001", copy.getProcessNo());

        ArgumentCaptor<ProcessOperation> opCaptor = ArgumentCaptor.forClass(ProcessOperation.class);
        verify(processOperationMapper, times(1)).insert(opCaptor.capture());
        assertEquals(Long.valueOf(2L), opCaptor.getValue().getProcessId());
        assertEquals(Integer.valueOf(1), opCaptor.getValue().getSeq());
        assertEquals("粗车", opCaptor.getValue().getOperationName());

        ArgumentCaptor<BomItem> bomCaptor = ArgumentCaptor.forClass(BomItem.class);
        verify(bomItemMapper, times(1)).insert(bomCaptor.capture());
        assertEquals(Long.valueOf(2L), bomCaptor.getValue().getProcessId());
        assertEquals("MT-001", bomCaptor.getValue().getMatCode());
    }

    @Test
    void createdVersionIsEditable() {
        Process source = process("PUBLISHED", 1);
        when(processMapper.selectById(1L)).thenReturn(source);
        when(processMapper.selectMaxVersionIncludingDeleted("PR-202611-0001")).thenReturn(1);
        doAnswer(invocation -> {
            Process copy = invocation.getArgument(0);
            copy.setId(2L);
            return 1;
        }).when(processMapper).insert(any(Process.class));
        when(processOperationMapper.selectList(any())).thenReturn(List.of());
        when(bomItemMapper.selectList(any())).thenReturn(List.of());

        Process copy = processService.createVersion(1L);
        when(processMapper.selectById(2L)).thenReturn(copy);

        assertDoesNotThrow(() -> processService.requireEditable(2L));
    }

    @Test
    void nextVersionSkipsSoftDeletedHighestVersion() {
        Process source = process("PUBLISHED", 1);
        when(processMapper.selectById(1L)).thenReturn(source);
        // 2 号版本已被逻辑删除，但唯一键 (process_no, version) 仍占用 2。
        when(processMapper.selectMaxVersionIncludingDeleted("PR-202611-0001")).thenReturn(2);
        doAnswer(invocation -> {
            Process copy = invocation.getArgument(0);
            copy.setId(3L);
            return 1;
        }).when(processMapper).insert(any(Process.class));
        when(processOperationMapper.selectList(any())).thenReturn(List.of());
        when(bomItemMapper.selectList(any())).thenReturn(List.of());

        Process copy = processService.createVersion(1L);

        assertEquals(Integer.valueOf(3), copy.getVersion(), "逻辑删除的版本号不可复用");
        assertEquals("PR-202611-0001", copy.getProcessNo());
    }
}
