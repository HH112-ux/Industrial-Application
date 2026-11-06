package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhizao.common.BusinessException;
import com.zhizao.common.PageResult;
import com.zhizao.entity.Device;
import com.zhizao.enums.DeviceStatus;
import com.zhizao.mapper.DeviceMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 设备台账服务：设备增删改查、状态变更，并按最近上报时间计算在线标记。
 *
 * @author Caspian
 */
@Service
public class DeviceService {

    private final DeviceMapper deviceMapper;
    private final CodeGenerator codeGenerator;

    @Value("${device.online-threshold-seconds:90}")
    private long onlineThresholdSeconds;

    public DeviceService(DeviceMapper deviceMapper, CodeGenerator codeGenerator) {
        this.deviceMapper = deviceMapper;
        this.codeGenerator = codeGenerator;
    }

    /**
     * 分页查询设备，并为每条记录计算在线标记。
     *
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页条数
     * @param keyword  设备编号、名称、制造商或型号的模糊匹配关键字，可为空
     * @param status   设备状态过滤，可为空
     * @return 设备分页结果
     */
    public PageResult<Device> list(int pageNum, int pageSize, String keyword, String status) {
        Page<Device> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Device> qw = new LambdaQueryWrapper<>();
        qw.and(StringUtils.hasText(keyword), w -> w
                .like(Device::getDeviceNo, keyword)
                .or().like(Device::getDeviceName, keyword)
                .or().like(Device::getManufacturer, keyword)
                .or().like(Device::getModel, keyword));
        qw.eq(StringUtils.hasText(status), Device::getStatus, status);
        qw.orderByDesc(Device::getId);
        deviceMapper.selectPage(page, qw);
        page.getRecords().forEach(this::applyOnline);
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    /**
     * 查询设备详情并计算在线标记。
     *
     * @param id 设备主键
     * @return 设备详情
     * @throws com.zhizao.common.BusinessException 设备不存在时抛出
     */
    public Device getById(Long id) {
        Device device = deviceMapper.selectById(id);
        if (device == null) {
            throw new BusinessException("设备不存在");
        }
        applyOnline(device);
        return device;
    }

    public Device create(Device device) {
        device.setId(null);
        device.setDeviceNo(codeGenerator.next("EQ"));
        if (device.getStatus() == null) {
            device.setStatus(DeviceStatus.STOPPED.name());
        }
        deviceMapper.insert(device);
        return device;
    }

    public Device update(Device device) {
        getById(device.getId());
        device.setDeviceNo(null); // 编号不可修改
        deviceMapper.updateById(device);
        return getById(device.getId());
    }

    public void delete(Long id) {
        getById(id);
        deviceMapper.deleteById(id);
    }

    public Device changeStatus(Long id, String status) {
        Device device = getById(id);
        try {
            device.setStatus(DeviceStatus.valueOf(status).name());
        } catch (Exception e) {
            throw new BusinessException("非法设备状态：" + status);
        }
        deviceMapper.updateById(device);
        return device;
    }

    /**
     * 按最近上报时间计算并写入在线标记，不改变设备状态字段。
     *
     * @param device 待计算在线标记的设备
     */
    private void applyOnline(Device device) {
        LocalDateTime lastSeenAt = device.getLastSeenAt();
        boolean online = lastSeenAt != null
                && lastSeenAt.isAfter(LocalDateTime.now().minusSeconds(onlineThresholdSeconds));
        device.setOnline(online);
    }
}
