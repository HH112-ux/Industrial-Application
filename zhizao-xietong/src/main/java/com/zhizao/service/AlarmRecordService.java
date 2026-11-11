package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhizao.common.BusinessException;
import com.zhizao.common.PageResult;
import com.zhizao.entity.AlarmRecord;
import com.zhizao.entity.Device;
import com.zhizao.enums.AlarmStatus;
import com.zhizao.mapper.AlarmRecordMapper;
import com.zhizao.mapper.DeviceMapper;
import com.zhizao.security.DataScope;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 报警中心服务：按车间数据域查询报警，并提供确认与恢复。
 *
 * <p>报警记录只保存设备主键，车间过滤先取数据域内设备主键再收敛记录；
 * 非管理员未分配车间时列表与未读数直接返回空，不发查询。
 * 确认与恢复使用条件更新，只有状态仍满足前置条件的调用会成功，
 * 并发下第二个调用得到 409 而不是覆盖别人的处理结果。</p>
 *
 * @author Caspian
 */
@Service
public class AlarmRecordService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final AlarmRecordMapper recordMapper;
    private final DeviceMapper deviceMapper;
    private final DataScope dataScope;

    public AlarmRecordService(AlarmRecordMapper recordMapper, DeviceMapper deviceMapper, DataScope dataScope) {
        this.recordMapper = recordMapper;
        this.deviceMapper = deviceMapper;
        this.dataScope = dataScope;
    }

    /** 分页查询，按开始时间倒序；{@code status} 为空表示全部状态。 */
    public PageResult<AlarmRecord> list(int pageNum, int pageSize, String status) {
        List<Long> deviceIds = scopedDeviceIds();
        if (deviceIds != null && deviceIds.isEmpty()) {
            return PageResult.of(0, List.of());
        }
        int size = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        Page<AlarmRecord> page = new Page<>(Math.max(pageNum, 1), size);
        recordMapper.selectPage(page, new LambdaQueryWrapper<AlarmRecord>()
                .eq(StringUtils.hasText(status), AlarmRecord::getStatus, status)
                .in(deviceIds != null, AlarmRecord::getDeviceId, deviceIds)
                .orderByDesc(AlarmRecord::getStartTime).orderByDesc(AlarmRecord::getId));
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    /** 未读数：数据域内状态为 OPEN（未处理）的记录数。 */
    public long unreadCount() {
        List<Long> deviceIds = scopedDeviceIds();
        if (deviceIds != null && deviceIds.isEmpty()) {
            return 0;
        }
        Long count = recordMapper.selectCount(new LambdaQueryWrapper<AlarmRecord>()
                .eq(AlarmRecord::getStatus, AlarmStatus.OPEN.name())
                .in(deviceIds != null, AlarmRecord::getDeviceId, deviceIds));
        return count == null ? 0 : count;
    }

    /** 确认报警，仅 OPEN 可流转；已被他人处理时抛出 409。 */
    public AlarmRecord acknowledge(Long id, String username) {
        requireScoped(id);
        int rows = recordMapper.update(null, new LambdaUpdateWrapper<AlarmRecord>()
                .eq(AlarmRecord::getId, id)
                .eq(AlarmRecord::getStatus, AlarmStatus.OPEN.name())
                .set(AlarmRecord::getStatus, AlarmStatus.ACKNOWLEDGED.name())
                .set(AlarmRecord::getAckUser, username)
                .set(AlarmRecord::getAckTime, LocalDateTime.now()));
        if (rows == 0) {
            throw conflict(id, "确认");
        }
        return reload(id);
    }

    /** 恢复报警，仅 OPEN / ACKNOWLEDGED 可流转；已恢复时抛出 409。 */
    public AlarmRecord recover(Long id) {
        requireScoped(id);
        int rows = recordMapper.update(null, new LambdaUpdateWrapper<AlarmRecord>()
                .eq(AlarmRecord::getId, id)
                .in(AlarmRecord::getStatus, AlarmStatus.OPEN.name(), AlarmStatus.ACKNOWLEDGED.name())
                .set(AlarmRecord::getStatus, AlarmStatus.RECOVERED.name())
                .set(AlarmRecord::getRecoverTime, LocalDateTime.now())
                .setSql("active_flag = NULL"));
        if (rows == 0) {
            throw conflict(id, "恢复");
        }
        return reload(id);
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

    private AlarmRecord requireScoped(Long id) {
        AlarmRecord record = reload(id);
        Device device = deviceMapper.selectById(record.getDeviceId());
        if (device == null) {
            throw new BusinessException(404, "报警记录所属设备不存在");
        }
        dataScope.requireWorkshop(device.getWorkshop());
        return record;
    }

    private AlarmRecord reload(Long id) {
        AlarmRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(404, "报警记录不存在");
        }
        return record;
    }

    /** 条件更新影响 0 行时，按最新状态给出可读的冲突提示。 */
    private BusinessException conflict(Long id, String action) {
        AlarmRecord latest = recordMapper.selectById(id);
        boolean recovered = latest != null && AlarmStatus.RECOVERED.name().equals(latest.getStatus());
        return new BusinessException(409, recovered
                ? "该报警已恢复，无需" + action
                : "该报警已被他人处理，请刷新后重试");
    }
}
