package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhizao.common.BusinessException;
import com.zhizao.common.PageResult;
import com.zhizao.entity.Process;
import com.zhizao.enums.ApprovalStatus;
import com.zhizao.mapper.ProcessMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProcessService {

    private final ProcessMapper processMapper;
    private final CodeGenerator codeGenerator;

    public ProcessService(ProcessMapper processMapper, CodeGenerator codeGenerator) {
        this.processMapper = processMapper;
        this.codeGenerator = codeGenerator;
    }

    public PageResult<Process> list(int pageNum, int pageSize, String keyword, String approvalStatus) {
        Page<Process> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Process> qw = new LambdaQueryWrapper<>();
        qw.and(StringUtils.hasText(keyword), w -> w
                .like(Process::getProcessNo, keyword)
                .or().like(Process::getProcessName, keyword)
                .or().like(Process::getOwner, keyword));
        qw.eq(StringUtils.hasText(approvalStatus), Process::getApprovalStatus, approvalStatus);
        qw.orderByDesc(Process::getId);
        processMapper.selectPage(page, qw);
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    public Process getById(Long id) {
        Process process = processMapper.selectById(id);
        if (process == null) {
            throw new BusinessException("工艺不存在");
        }
        return process;
    }

    /** 内部查询，未找到返回 null（供齐套校验使用）。 */
    public Process findById(Long id) {
        return processMapper.selectById(id);
    }

    @CacheEvict(cacheNames = "process", allEntries = true)
    public Process create(Process process) {
        process.setId(null);
        process.setProcessNo(codeGenerator.next("PR"));
        process.setApprovalStatus(ApprovalStatus.DRAFT.name());
        processMapper.insert(process);
        return process;
    }

    public Process update(Process process) {
        getById(process.getId());
        process.setProcessNo(null);
        process.setApprovalStatus(null); // 审批状态走专用流转，不允许直接改
        processMapper.updateById(process);
        return getById(process.getId());
    }

    public void delete(Long id) {
        getById(id);
        processMapper.deleteById(id);
    }

    @CacheEvict(cacheNames = "process", allEntries = true)
    public Process submit(Long id) {
        Process process = getById(id);
        requireStatus(process, ApprovalStatus.DRAFT, "仅草稿可提交审批");
        process.setApprovalStatus(ApprovalStatus.PENDING.name());
        processMapper.updateById(process);
        return process;
    }

    public Process approve(Long id) {
        Process process = getById(id);
        requireStatus(process, ApprovalStatus.PENDING, "仅审批中可发布");
        process.setApprovalStatus(ApprovalStatus.PUBLISHED.name());
        processMapper.updateById(process);
        return process;
    }

    @CacheEvict(cacheNames = "process", allEntries = true)
    public Process reject(Long id) {
        Process process = getById(id);
        requireStatus(process, ApprovalStatus.PENDING, "仅审批中可驳回");
        process.setApprovalStatus(ApprovalStatus.REJECTED.name());
        processMapper.updateById(process);
        return process;
    }

    private void requireStatus(Process process, ApprovalStatus expected, String message) {
        if (!expected.name().equals(process.getApprovalStatus())) {
            throw new BusinessException(message);
        }
    }
}
