package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.common.BadRequestException;
import com.zhizao.common.BusinessException;
import com.zhizao.entity.AlarmRule;
import com.zhizao.entity.Device;
import com.zhizao.mapper.AlarmRuleMapper;
import com.zhizao.mapper.DeviceMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 报警规则服务：管理员维护阈值规则，同一设备+指标+规则类型只允许一条。
 *
 * @author Caspian
 */
@Service
public class AlarmRuleService {

    /** 阈值规则允许的指标，与遥测实际字段保持一致。 */
    private static final Set<String> METRICS = Set.of("temperature", "rpm", "current");
    private static final String THRESHOLD = "THRESHOLD";
    private static final int DEFAULT_CONSECUTIVE_TIMES = 3;
    private static final int MAX_CONSECUTIVE_TIMES = 100;

    private final AlarmRuleMapper ruleMapper;
    private final DeviceMapper deviceMapper;

    public AlarmRuleService(AlarmRuleMapper ruleMapper, DeviceMapper deviceMapper) {
        this.ruleMapper = ruleMapper;
        this.deviceMapper = deviceMapper;
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
    /** 更新规则，设备归属不可修改。 */
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
            ruleMapper.updateById(rule);
        } catch (DuplicateKeyException e) {
            throw duplicateRule();
        }
        return ruleMapper.selectById(id);
    }
    /** 删除规则。 */
    public void delete(Long id) {
        require(id);
        ruleMapper.deleteById(id);
    }
    private AlarmRule require(Long id) {
        AlarmRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException("报警规则不存在");
        }
        return rule;
    }
    private void normalize(AlarmRule rule) {
        if (rule.getRuleType() == null || !THRESHOLD.equalsIgnoreCase(rule.getRuleType().trim())) {
            throw new BadRequestException("当前仅支持 THRESHOLD 报警规则");
        }
        rule.setRuleType(THRESHOLD);
        rule.setCurrentStreak(0);
        if (rule.getEnabled() == null) {
            rule.setEnabled(Boolean.TRUE);
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
