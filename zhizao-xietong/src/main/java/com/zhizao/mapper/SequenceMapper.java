package com.zhizao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhizao.entity.Sequence;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SequenceMapper extends BaseMapper<Sequence> {

    /** 确保序列存在（不存在则初始化为 0），并发安全。 */
    @Insert("INSERT INTO sys_sequence (seq_name, current_value) VALUES (#{name}, 0) ON DUPLICATE KEY UPDATE seq_name = seq_name")
    int ensure(@Param("name") String name);

    /** 行级锁读取序列当前值，用于原子自增。 */
    @Select("SELECT seq_name, current_value FROM sys_sequence WHERE seq_name = #{name} FOR UPDATE")
    Sequence selectForUpdate(@Param("name") String name);
}
