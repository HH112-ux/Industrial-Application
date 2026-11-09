package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhizao.common.BusinessException;
import com.zhizao.common.PageResult;
import com.zhizao.entity.BomItem;
import com.zhizao.entity.Process;
import com.zhizao.entity.ProcessOperation;
import com.zhizao.enums.ApprovalStatus;
import com.zhizao.mapper.BomItemMapper;
import com.zhizao.mapper.ProcessMapper;
import com.zhizao.mapper.ProcessOperationMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 工艺服务：负责版本化、审批流转与版本复制。
 *
 * <p>同一 {@code processNo} 的每一行是一个独立版本，发布后不可再编辑；
 * 变更通过 {@link #createVersion(Long)} 复制出下一版草稿完成。</p>
 *
 * @author Caspian
 */
@Service
public class ProcessService {

    private final ProcessMapper processMapper;
    private final BomItemMapper bomItemMapper;
    private final ProcessOperationMapper processOperationMapper;
    private final CodeGenerator codeGenerator;

    public ProcessService(ProcessMapper processMapper, BomItemMapper bomItemMapper,
                          ProcessOperationMapper processOperationMapper, CodeGenerator codeGenerator) {
        this.processMapper = processMapper;
        this.bomItemMapper = bomItemMapper;
        this.processOperationMapper = processOperationMapper;
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

    /**
     * 校验工艺存在且仍可编辑。
     *
     * @param id 工艺版本 ID
     * @return 工艺实体
     * @throws BusinessException 工艺不存在或已发布
     */
    public Process requireEditable(Long id) {
        Process process = getById(id);
        if (ApprovalStatus.PUBLISHED.name().equals(process.getApprovalStatus())) {
            throw new BusinessException("已发布工艺不可编辑，请新建版本");
        }
        return process;
    }

    @CacheEvict(cacheNames = "process", allEntries = true)
    public Process create(Process process) {
        process.setId(null);
        process.setProcessNo(codeGenerator.next("PR"));
        process.setVersion(1);
        process.setApprovalStatus(ApprovalStatus.DRAFT.name());
        processMapper.insert(process);
        return process;
    }

    /**
     * 修改工艺基础信息，仅未发布版本可改。
     *
     * @param process 待修改的工艺，必须携带主键
     * @return 修改后的工艺
     */
    public Process update(Process process) {
        requireEditable(process.getId());
        process.setProcessNo(null);
        process.setVersion(null); // 版本号由版本复制维护
        process.setApprovalStatus(null); // 审批状态走专用流转，不允许直接改
        processMapper.updateById(process);
        return getById(process.getId());
    }

    public void delete(Long id) {
        getById(id);
        processMapper.deleteById(id);
    }

    /**
     * 提交审批：必须已有工序和 BOM，否则按业务规则拒绝。
     *
     * @param id 工艺版本 ID
     * @return 审批中的工艺
     */
    @CacheEvict(cacheNames = "process", allEntries = true)
    public Process submit(Long id) {
        Process process = getById(id);
        requireStatus(process, ApprovalStatus.DRAFT, "仅草稿可提交审批");
        List<String> missing = new ArrayList<>();
        if (countOperations(id) == 0) {
            missing.add("工序");
        }
        if (countBomItems(id) == 0) {
            missing.add("BOM 物料");
        }
        if (!missing.isEmpty()) {
            throw new BusinessException("提交审批前请先添加" + String.join("与", missing));
        }
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

    /**
     * 基于指定版本复制出下一个草稿版本，工序与 BOM 一并复制。
     *
     * @param id 源工艺版本 ID
     * @return 新建的草稿版本
     */
    @CacheEvict(cacheNames = "process", allEntries = true)
    @Transactional
    public Process createVersion(Long id) {
        Process source = getById(id);
        Process copy = new Process();
        copy.setProcessNo(source.getProcessNo());
        copy.setVersion(nextVersion(source));
        copy.setProcessName(source.getProcessName());
        copy.setProcessType(source.getProcessType());
        copy.setOwner(source.getOwner());
        copy.setDeviceMatReq(source.getDeviceMatReq());
        copy.setApprovalStatus(ApprovalStatus.DRAFT.name());
        processMapper.insert(copy);

        List<ProcessOperation> operations = processOperationMapper.selectList(
                new LambdaQueryWrapper<ProcessOperation>()
                        .eq(ProcessOperation::getProcessId, id)
                        .orderByAsc(ProcessOperation::getSeq));
        for (ProcessOperation operation : operations) {
            ProcessOperation cloned = new ProcessOperation();
            cloned.setProcessId(copy.getId());
            cloned.setSeq(operation.getSeq());
            cloned.setOperationName(operation.getOperationName());
            cloned.setDeviceType(operation.getDeviceType());
            cloned.setEstHours(operation.getEstHours());
            cloned.setSetupHours(operation.getSetupHours());
            processOperationMapper.insert(cloned);
        }

        List<BomItem> items = bomItemMapper.selectList(
                new LambdaQueryWrapper<BomItem>().eq(BomItem::getProcessId, id));
        for (BomItem item : items) {
            BomItem cloned = new BomItem();
            cloned.setProcessId(copy.getId());
            cloned.setMatCode(item.getMatCode());
            cloned.setRequiredQty(item.getRequiredQty());
            bomItemMapper.insert(cloned);
        }
        return copy;
    }

    private int nextVersion(Process source) {
        Integer maxVersion = processMapper.selectMaxVersionIncludingDeleted(source.getProcessNo());
        return maxVersion == null ? 1 : maxVersion + 1;
    }

    private long countOperations(Long processId) {
        Long count = processOperationMapper.selectCount(
                new LambdaQueryWrapper<ProcessOperation>().eq(ProcessOperation::getProcessId, processId));
        return count == null ? 0 : count;
    }

    private long countBomItems(Long processId) {
        Long count = bomItemMapper.selectCount(
                new LambdaQueryWrapper<BomItem>().eq(BomItem::getProcessId, processId));
        return count == null ? 0 : count;
    }

    private void requireStatus(Process process, ApprovalStatus expected, String message) {
        if (!expected.name().equals(process.getApprovalStatus())) {
            throw new BusinessException(message);
        }
    }
}
