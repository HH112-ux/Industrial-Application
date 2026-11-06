package com.zhizao.service;

import com.zhizao.common.BusinessException;
import com.zhizao.entity.Process;
import com.zhizao.entity.ProcessOperation;
import com.zhizao.mapper.ProcessOperationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessOperationServiceTest {

    @Mock
    private ProcessOperationMapper processOperationMapper;
    @Mock
    private ProcessService processService;

    @InjectMocks
    private ProcessOperationService processOperationService;

    private ProcessOperation operation(int seq) {
        ProcessOperation operation = new ProcessOperation();
        operation.setSeq(seq);
        operation.setOperationName("精车");
        return operation;
    }

    @Test
    void createRejectsDuplicateSeq() {
        when(processService.requireEditable(1L)).thenReturn(new Process());
        when(processOperationMapper.selectCount(any())).thenReturn(1L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> processOperationService.create(1L, operation(1)));

        assertEquals("工序顺序 1 已存在", error.getMessage());
        verify(processOperationMapper, never()).insert(any(ProcessOperation.class));
    }

    @Test
    void createRejectsPublishedProcess() {
        when(processService.requireEditable(1L))
                .thenThrow(new BusinessException("已发布工艺不可编辑，请新建版本"));

        assertThrows(BusinessException.class, () -> processOperationService.create(1L, operation(1)));
        verify(processOperationMapper, never()).insert(any(ProcessOperation.class));
    }

    @Test
    void createRejectsBlankName() {
        when(processService.requireEditable(1L)).thenReturn(new Process());
        ProcessOperation operation = operation(1);
        operation.setOperationName("  ");

        assertThrows(BusinessException.class, () -> processOperationService.create(1L, operation));
        verify(processOperationMapper, never()).insert(any(ProcessOperation.class));
    }
}
