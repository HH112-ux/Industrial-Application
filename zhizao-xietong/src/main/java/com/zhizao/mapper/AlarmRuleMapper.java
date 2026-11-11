package com.zhizao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhizao.entity.AlarmRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 报警规则数据访问。
 *
 * @author Caspian
 */
@Mapper
public interface AlarmRuleMapper extends BaseMapper<AlarmRule> {
}
