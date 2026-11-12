package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhizao.common.BadRequestException;
import com.zhizao.common.BusinessException;
import com.zhizao.entity.AlarmRecord;
import com.zhizao.entity.AlarmRule;
import com.zhizao.entity.Device;
import com.zhizao.enums.AlarmStatus;
import com.zhizao.mapper.AlarmRecordMapper;
import com.zhizao.mapper.AlarmRuleMapper;
import com.zhizao.mapper.DeviceMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 报警规则服务：管理员维护阈值与离线规则，同一设备+指标+规则类型只允许一条。
 *
 * <p>离线规则不配置指标与阈值，统一复用 {@code device.online-threshold-seconds}，
 * 因此指标固定为空串，同一设备最多一条离线规则。</p>
 *
 * <p>判定链路只遍历启用中的规则，因此规则停用或删除时必须在同一事务内闭合对应在途记录，
 * 否则这些记录会永久停留在未恢复状态并持续计入报警中心未读数。</p>
 *
 * @author Caspian
 */
@Service
public class AlarmRuleService {

    /** 阈值规则允许的指标，与遥测实际字段保持一致。 */
    private static final Set<String> METRICS = Set.of("temperature", "rpm", "current");
    private static final String THRESHOLD = "THRESHOLD";
    private static final String OFFLINE = "OFFLINE";
    private static final int DEFAULT_CONSECUTIVE_TIMES = 3;
    private static final int MAX_CONSECUTIVE_TIMES = 100;

    private final AlarmRuleMapper ruleMapper;
    private final DeviceMapper deviceMapper;
    private final AlarmRecordMapper recordMapper;

    public AlarmRuleService(AlarmRuleMapper ruleMapper, DeviceMapper deviceMapper,
                            AlarmRecordMapper recordMapper) {
        this.ruleMapper = ruleMapper;
        this.deviceMapper = deviceMapper;
        this.recordMapper = recordMapper;
    }
    /** 查询规则列表，{@code deviceId} 为空表示全部。 */
    public List<AlarmRule> list(Long deviceId) {
        return ruleMapper.selectList(new LambdaQueryWrapper<AlarmRule>()
                .eq(deviceId != null, AlarmRule::getDeviceId, deviceId)
                .orderByAsc(AlarmRule::getDeviceId).orderByAsc(AlarmRule::getId));
    }
    /** 新增阈值规则。 */
    public AlarmRule create(AlarmRule rule) {
        if (rule.getDeviceId() == null) {
            throw new BadRequestException("必须指定设备");
        }
        rule.setId(null);
        Device device = deviceMapper.selectById(rule.getDeviceId());
        if (device == null) {
            throw new BusinessException("设备不存在");
        }
        normalize(rule);
        ensureUnique(rule, null);
        try {
            ruleMapper.insert(rule);
        } catch (DuplicateKeyException e) {
            throw duplicateRule();
        }
        return rule;
    }
    /** 更新规则，设备归属不可修改；停用时同时闭合在途报警记录。 */
    @Transactional
    public AlarmRule update(Long id, AlarmRule rule) {
        AlarmRule existing = require(id);
        rule.setId(id);
        rule.setDeviceId(existing.getDeviceId());
        if (rule.getEnabled() == null) {
            rule.setEnabled(existing.getEnabled());
        }
        if (rule.getConsecutiveTimes() == null) {
            rule.setConsecutiveTimes(existing.getConsecutiveTimes());
        }
        normalize(rule);
        ensureUnique(rule, id);
        try {
            // 只写管理员可配置的列：current_streak 由判定链路原子维护，
            // 整实体回写会用旧快照覆盖并发自增，也会把阈值改回提交前的值。
            ruleMapper.update(null, new LambdaUpdateWrapper<AlarmRule>()
                    .eq(AlarmRule::getId, id)
                    .set(AlarmRule::getMetric, rule.getMetric())
                    .set(AlarmRule::getRuleType, rule.getRuleType())
                    .set(AlarmRule::getUpperLimit, rule.getUpperLimit())
                    .set(AlarmRule::getLowerLimit, rule.getLowerLimit())
                    .set(AlarmRule::getConsecutiveTimes, rule.getConsecutiveTimes())
                    .set(AlarmRule::getEnabled, rule.getEnabled()));
        } catch (DuplicateKeyException e) {
            throw duplicateRule();
        }
        if (!Boolean.TRUE.equals(rule.getEnabled())) {
            closeActiveRecords(existing.getDeviceId(), existing.getRuleType(), existing.getMetric());
        }
        return ruleMapper.selectById(id);
    }
    /** 删除规则，同时闭合其在途报警记录。 */
    @Transactional
    public void delete(Long id) {
        AlarmRule existing = require(id);
        ruleMapper.deleteById(id);
        closeActiveRecords(existing.getDeviceId(), existing.getRuleType(), existing.getMetric());
    }

    /**
     * 闭合规则对应的在途报警记录。
     *
     * <p>条件更新只处理仍在途的记录，重复调用幂等；规则已不参与判定，
     * 因此按「规则终止」口径置为已恢复并记录恢复时间，避免永久计入未读数。</p>
     *
     * @param deviceId 设备主键
     * @param ruleType 规则类型
     * @param metric   指标，离线规则为空串
     */
    private void closeActiveRecords(Long deviceId, String ruleType, String metric) {
        recordMapper.update(null, new LambdaUpdateWrapper<AlarmRecord>()
                .eq(AlarmRecord::getDeviceId, deviceId)
                .eq(AlarmRecord::getRuleType, ruleType)
                .eq(AlarmRecord::getMetric, metric)
                .eq(AlarmRecord::getActiveFlag, 1)
                .in(AlarmRecord::getStatus, AlarmStatus.OPEN.name(), AlarmStatus.ACKNOWLEDGED.name())
                .set(AlarmRecord::getStatus, AlarmStatus.RECOVERED.name())
                .set(AlarmRecord::getRecoverTime, LocalDateTime.now())
                .setSql("active_flag = NULL"));
    }
    private AlarmRule require(Long id) {
        AlarmRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException("报警规则不存在");
        }
        return rule;
    }
    private void normalize(AlarmRule rule) {
        String type = rule.getRuleType() == null ? "" : rule.getRuleType().trim().toUpperCase();
        if (!THRESHOLD.equals(type) && !OFFLINE.equals(type)) {
            throw new BadRequestException("规则类型必须为 THRESHOLD 或 OFFLINE");
        }
        rule.setRuleType(type);
        rule.setCurrentStreak(0);
        if (rule.getEnabled() == null) {
            rule.setEnabled(Boolean.TRUE);
        }
        if (OFFLINE.equals(type)) {
            // 离线规则复用全局在线阈值，不配置指标与上下限。
            rule.setMetric("");
            rule.setUpperLimit(null);
            rule.setLowerLimit(null);
            rule.setConsecutiveTimes(DEFAULT_CONSECUTIVE_TIMES);
            return;
        }
        String metric = rule.getMetric() == null ? "" : rule.getMetric().trim();
        if (!METRICS.contains(metric)) {
            throw new BadRequestException("指标必须为 temperature、rpm 或 current");
        }
        rule.setMetric(metric);
        if (rule.getUpperLimit() == null && rule.getLowerLimit() == null) {
            throw new BadRequestException("阈值规则至少需要配置上限或下限");
        }
        if (rule.getUpperLimit() != null && rule.getLowerLimit() != null
                && rule.getUpperLimit() <= rule.getLowerLimit()) {
            throw new BadRequestException("上限必须大于下限");
        }
        int times = rule.getConsecutiveTimes() == null ? DEFAULT_CONSECUTIVE_TIMES : rule.getConsecutiveTimes();
        if (times < 1 || times > MAX_CONSECUTIVE_TIMES) {
            throw new BadRequestException("持续次数必须在 1 到 100 之间");
        }
        rule.setConsecutiveTimes(times);
    }
    private void ensureUnique(AlarmRule rule, Long excludeId) {
        Long count = ruleMapper.selectCount(new LambdaQueryWrapper<AlarmRule>()
                .eq(AlarmRule::getDeviceId, rule.getDeviceId())
                .eq(AlarmRule::getRuleType, rule.getRuleType())
                .eq(AlarmRule::getMetric, rule.getMetric())
                .ne(excludeId != null, AlarmRule::getId, excludeId));
        if (count != null && count > 0) {
            throw duplicateRule();
        }
    }
    private BusinessException duplicateRule() {
        return new BusinessException(409, "同一设备同一指标同一规则类型只允许一条规则，请修改或删除原规则");
    }
}
