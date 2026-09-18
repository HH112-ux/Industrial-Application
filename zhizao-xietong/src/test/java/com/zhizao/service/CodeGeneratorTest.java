package com.zhizao.service;

import com.zhizao.entity.Sequence;
import com.zhizao.mapper.SequenceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodeGeneratorTest {

    @Mock
    private SequenceMapper sequenceMapper;

    @InjectMocks
    private CodeGenerator codeGenerator;

    @Test
    void generatesCodeWithCorrectFormat() {
        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        Sequence seq = new Sequence();
        seq.setSeqName("EQ-" + month);
        seq.setCurrentValue(5L);
        when(sequenceMapper.selectForUpdate("EQ-" + month)).thenReturn(seq);

        String code = codeGenerator.next("EQ");

        assertEquals("EQ-" + month + "-0006", code);
        assertEquals(6L, seq.getCurrentValue());
        verify(sequenceMapper).ensure("EQ-" + month);
        verify(sequenceMapper).updateById(seq);
    }
}
