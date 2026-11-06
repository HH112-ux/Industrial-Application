package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.common.BusinessException;
import com.zhizao.entity.ProcessOperation;
import com.zhizao.mapper.ProcessOperationMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 工艺工序服务：维护一个工艺版本下的有序工序路线，已发布版本不可修改。
 *
 * @author Caspian
 */
@Service
public class ProcessOperationService {

    private final ProcessOperationMapper processOperationMapper;
    private final ProcessService processService;

    public ProcessOperationService(ProcessOperationMapper processOperationMapper, ProcessService processService) {
        this.processOperationMapper = processOperationMapper;
        this.processService = processService;
    }

    /**
     * 按顺序列出工艺版本下的工序。
     *
     * @param processId 工艺版本 ID
     * @return 按 seq 升序的工序列表
     */
    public List<ProcessOperation> list(Long processId) {
        return processOperationMapper.selectList(new LambdaQueryWrapper<ProcessOperation>()
                .eq(ProcessOperation::getProcessId, processId)
                .orderByAsc(ProcessOperation::getSeq));
    }

    /**
     * 新增工序。
     *
     * @param processId 工艺版本 ID
     * @param operation 工序内容
     * @return 落库后的工序
     */
    public ProcessOperation create(Long processId, ProcessOperation operation) {
        processService.requireEditable(processId);
        validate(operation);
        requireSeqAvailable(processId, operation.getSeq(), null);
        operation.setId(null);
        operation.setProcessId(processId);
        processOperationMapper.insert(operation);
        return operation;
    }

    /**
     * 修改工序。
     *
     * @param processId 工艺版本 ID
     * @param id        工序 ID
     * @param operation 新的工序内容
     * @return 修改后的工序
     */
    public ProcessOperation update(Long processId, Long id, ProcessOperation operation) {
        processService.requireEditable(processId);
        requireOwned(processId, id);
        validate(operation);
        requireSeqAvailable(processId, operation.getSeq(), id);
        operation.setId(id);
        operation.setProcessId(processId);
        processOperationMapper.updateById(operation);
        return operation;
    }

    /**
     * 删除工序。
     *
     * @param processId 工艺版本 ID
     * @param id        工序 ID
     */
    public void delete(Long processId, Long id) {
        processService.requireEditable(processId);
        requireOwned(processId, id);
        processOperationMapper.deleteById(id);
    }

    private void validate(ProcessOperation operation) {
        if (!StringUtils.hasText(operation.getOperationName())) {
            throw new BusinessException("工序名称不能为空");
        }
        if (operation.getSeq() == null || operation.getSeq() < 1) {
            throw new BusinessException("工序顺序必须从 1 开始");
        }
    }

    private void requireSeqAvailable(Long processId, Integer seq, Long excludeId) {
        Long count = processOperationMapper.selectCount(new LambdaQueryWrapper<ProcessOperation>()
                .eq(ProcessOperation::getProcessId, processId)
                .eq(ProcessOperation::getSeq, seq)
                .ne(excludeId != null, ProcessOperation::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("工序顺序 " + seq + " 已存在");
        }
    }

    private void requireOwned(Long processId, Long id) {
        ProcessOperation operation = processOperationMapper.selectById(id);
        if (operation == null || !processId.equals(operation.getProcessId())) {
            throw new BusinessException("工序不存在");
        }
    }
}
