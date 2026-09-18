package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhizao.common.BusinessException;
import com.zhizao.common.PageResult;
import com.zhizao.entity.MaintenanceRecord;
import com.zhizao.mapper.MaintenanceRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 设备点检/维修服务。
 */
@Service
public class MaintenanceService {

    private final MaintenanceRecordMapper maintenanceRecordMapper;
    private final CodeGenerator codeGenerator;

    public MaintenanceService(MaintenanceRecordMapper maintenanceRecordMapper, CodeGenerator codeGenerator) {
        this.maintenanceRecordMapper = maintenanceRecordMapper;
        this.codeGenerator = codeGenerator;
    }

    public PageResult<MaintenanceRecord> list(int pageNum, int pageSize, String keyword, String mtType) {
        Page<MaintenanceRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<MaintenanceRecord> qw = new LambdaQueryWrapper<>();
        qw.and(StringUtils.hasText(keyword), w -> w
                .like(MaintenanceRecord::getMtNo, keyword)
                .or().like(MaintenanceRecord::getDescription, keyword)
                .or().like(MaintenanceRecord::getOperator, keyword));
        qw.eq(StringUtils.hasText(mtType), MaintenanceRecord::getMtType, mtType);
        qw.orderByDesc(MaintenanceRecord::getId);
        maintenanceRecordMapper.selectPage(page, qw);
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    public MaintenanceRecord getById(Long id) {
        MaintenanceRecord record = maintenanceRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("记录不存在");
        }
        return record;
    }

    public MaintenanceRecord create(MaintenanceRecord record) {
        record.setId(null);
        record.setMtNo(codeGenerator.next("MN"));
        if (record.getMtTime() == null) {
            record.setMtTime(LocalDateTime.now());
        }
        if (!StringUtils.hasText(record.getResultStatus())) {
            record.setResultStatus("NORMAL");
        }
        maintenanceRecordMapper.insert(record);
        return record;
    }

    public MaintenanceRecord update(Long id, MaintenanceRecord record) {
        record.setId(id);
        record.setMtNo(null); // 编号不可修改
        maintenanceRecordMapper.updateById(record);
        return maintenanceRecordMapper.selectById(id);
    }

    public void delete(Long id) {
        maintenanceRecordMapper.deleteById(id);
    }
}
