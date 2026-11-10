package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhizao.common.BusinessException;
import com.zhizao.common.PageResult;
import com.zhizao.entity.Device;
import com.zhizao.enums.DeviceStatus;
import com.zhizao.mapper.DeviceMapper;
import com.zhizao.security.DataScope;
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
    private final DataScope dataScope;

    @Value("${device.online-threshold-seconds:90}")
    private long onlineThresholdSeconds;

    /**
     * 构造设备服务。
     *
     * @param deviceMapper  设备数据访问
     * @param codeGenerator 业务编号生成器
     * @param dataScope     车间数据域
     */
    public DeviceService(DeviceMapper deviceMapper, CodeGenerator codeGenerator, DataScope dataScope) {
        this.deviceMapper = deviceMapper;
        this.codeGenerator = codeGenerator;
        this.dataScope = dataScope;
    }

    /**
     * 分页查询设备，并为每条记录计算在线标记。
     *
     * <p>非管理员只能看到自己车间范围内的设备；管理端看板同样通过本查询取数。</p>
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
        String workshop = dataScope.workshopFilter();
        qw.eq(workshop != null, Device::getWorkshop, workshop);
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
     * @throws com.zhizao.common.ForbiddenException 设备不属于当前用户车间时抛出
     */
    public Device getById(Long id) {
        Device device = deviceMapper.selectById(id);
        if (device == null) {
            throw new BusinessException("设备不存在");
        }
        dataScope.requireWorkshop(device.getWorkshop());
        applyOnline(device);
        return device;
    }

    /**
     * 新增设备：车间必填，不再回退到固定车间。
     *
     * @param device 设备数据
     * @return 已保存的设备
     * @throws BusinessException 未指定车间时抛出
     */
    public Device create(Device device) {
        device.setId(null);
        device.setDeviceNo(codeGenerator.next("EQ"));
        if (device.getStatus() == null) {
            device.setStatus(DeviceStatus.STOPPED.name());
        }
        device.setWorkshop(requireWorkshop(device.getWorkshop(), "设备"));
        deviceMapper.insert(device);
        return device;
    }

    /**
     * 更新设备：车间传入时必须非空，传 null 表示不修改。
     *
     * @param device 设备数据
     * @return 更新后的设备
     * @throws BusinessException 车间传空时抛出
     */
    public Device update(Device device) {
        getById(device.getId());
        device.setDeviceNo(null); // 编号不可修改
        if (device.getWorkshop() != null) {
            device.setWorkshop(requireWorkshop(device.getWorkshop(), "设备"));
        }
        deviceMapper.updateById(device);
        return getById(device.getId());
    }

    /**
     * 校验并规范化车间必填值。
     *
     * @param workshop 车间值
     * @param subject  业务对象名称，用于错误文案
     * @return 去除首尾空白后的车间
     * @throws BusinessException 车间为空时抛出
     */
    private static String requireWorkshop(String workshop, String subject) {
        if (!StringUtils.hasText(workshop)) {
            throw new BusinessException(subject + "必须指定车间");
        }
        return workshop.trim();
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
