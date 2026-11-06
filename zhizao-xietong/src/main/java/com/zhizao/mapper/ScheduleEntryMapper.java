package com.zhizao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhizao.entity.ScheduleEntry;
import org.apache.ibatis.annotations.Mapper;

/**
 * 排程结果的数据访问入口。
 *
 * @author Caspian
 */
@Mapper
public interface ScheduleEntryMapper extends BaseMapper<ScheduleEntry> {
}
