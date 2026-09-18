package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhizao.common.BusinessException;
import com.zhizao.common.PageResult;
import com.zhizao.entity.Device;
import com.zhizao.enums.DeviceStatus;
import com.zhizao.mapper.DeviceMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DeviceService {

    private final DeviceMapper deviceMapper;
    private final CodeGenerator codeGenerator;

    public DeviceService(DeviceMapper deviceMapper, CodeGenerator codeGenerator) {
        this.deviceMapper = deviceMapper;
        this.codeGenerator = codeGenerator;
    }

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
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    public Device getById(Long id) {
        Device device = deviceMapper.selectById(id);
        if (device == null) {
            throw new BusinessException("设备不存在");
        }
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
}
