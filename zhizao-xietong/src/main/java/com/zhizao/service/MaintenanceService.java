package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhizao.common.BusinessException;
import com.zhizao.common.PageResult;
import com.zhizao.entity.Device;
import com.zhizao.entity.MaintenanceRecord;
import com.zhizao.mapper.DeviceMapper;
import com.zhizao.mapper.MaintenanceRecordMapper;
import com.zhizao.security.DataScope;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备点检/维修服务。
 *
 * <p>记录只保存设备主键，车间过滤先取数据域内设备主键再收敛记录；
 * 非管理员未分配车间时列表直接返回空，详情与写入按设备所属车间校验。
 * 管理员与内部调用不受限制。</p>
 *
 * @author Caspian
 */
@Service
public class MaintenanceService {

    private final MaintenanceRecordMapper maintenanceRecordMapper;
    private final DeviceMapper deviceMapper;
    private final CodeGenerator codeGenerator;
    private final DataScope dataScope;

    public MaintenanceService(MaintenanceRecordMapper maintenanceRecordMapper,
                              DeviceMapper deviceMapper,
                              CodeGenerator codeGenerator,
                              DataScope dataScope) {
        this.maintenanceRecordMapper = maintenanceRecordMapper;
        this.deviceMapper = deviceMapper;
        this.codeGenerator = codeGenerator;
        this.dataScope = dataScope;
    }

    public PageResult<MaintenanceRecord> list(int pageNum, int pageSize, String keyword, String mtType) {
        List<Long> deviceIds = scopedDeviceIds();
        if (deviceIds != null && deviceIds.isEmpty()) {
            return PageResult.of(0, List.of());
        }
        Page<MaintenanceRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<MaintenanceRecord> qw = new LambdaQueryWrapper<>();
        qw.and(StringUtils.hasText(keyword), w -> w
                .like(MaintenanceRecord::getMtNo, keyword)
                .or().like(MaintenanceRecord::getDescription, keyword)
                .or().like(MaintenanceRecord::getOperator, keyword));
        qw.eq(StringUtils.hasText(mtType), MaintenanceRecord::getMtType, mtType);
        qw.in(deviceIds != null, MaintenanceRecord::getDeviceId, deviceIds);
        qw.orderByDesc(MaintenanceRecord::getId);
        maintenanceRecordMapper.selectPage(page, qw);
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    public MaintenanceRecord getById(Long id) {
        return requireScoped(id);
    }

    public MaintenanceRecord create(MaintenanceRecord record) {
        dataScope.requireWorkshop(requireDevice(record.getDeviceId()).getWorkshop());
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
        requireScoped(id);
        if (record.getDeviceId() != null) {
            dataScope.requireWorkshop(requireDevice(record.getDeviceId()).getWorkshop());
        }
        record.setId(id);
        record.setMtNo(null); // 编号不可修改
        maintenanceRecordMapper.updateById(record);
        return maintenanceRecordMapper.selectById(id);
    }

    public void delete(Long id) {
        requireScoped(id);
        maintenanceRecordMapper.deleteById(id);
    }

    /** 返回数据域内设备主键；null 表示不限制，空列表表示无可见设备。 */
    private List<Long> scopedDeviceIds() {
        String workshop = dataScope.workshopFilter();
        if (workshop == null) {
            return null;
        }
        if (workshop.isEmpty()) {
            return List.of();
        }
        return deviceMapper.selectList(new LambdaQueryWrapper<Device>()
                        .select(Device::getId)
                        .eq(Device::getWorkshop, workshop))
                .stream().map(Device::getId).toList();
    }

    private MaintenanceRecord requireScoped(Long id) {
        MaintenanceRecord record = maintenanceRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(404, "记录不存在");
        }
        dataScope.requireWorkshop(requireDevice(record.getDeviceId()).getWorkshop());
        return record;
    }

    private Device requireDevice(Long deviceId) {
        Device device = deviceId == null ? null : deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException(404, "点检记录所属设备不存在");
        }
        return device;
    }
}
