package com.zhizao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhizao.entity.AlarmRecord;
import org.apache.ibatis.annotations.Mapper;

/** 报警记录数据访问。 */
@Mapper
public interface AlarmRecordMapper extends BaseMapper<AlarmRecord> {
}
