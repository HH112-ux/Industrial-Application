package com.zhizao.service;

import com.zhizao.entity.Sequence;
import com.zhizao.mapper.SequenceMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 业务编号生成器：前缀 + yyyyMM + 4 位流水。
 * 基于数据库序列表，按月分段、行级锁原子自增，并发安全、跨重启不重复。
 */
@Component
public class CodeGenerator {

    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

    private final SequenceMapper sequenceMapper;

    public CodeGenerator(SequenceMapper sequenceMapper) {
        this.sequenceMapper = sequenceMapper;
    }

    @Transactional
    public String next(String prefix) {
        String month = LocalDate.now().format(YYYYMM);
        String seqName = prefix + "-" + month;
        sequenceMapper.ensure(seqName);
        Sequence sequence = sequenceMapper.selectForUpdate(seqName);
        long next = sequence.getCurrentValue() + 1;
        sequence.setCurrentValue(next);
        sequenceMapper.updateById(sequence);
        return prefix + "-" + month + "-" + String.format("%04d", next);
    }
}
