package com.zhizao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhizao.entity.ProcessOperation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工艺工序列的数据访问入口。
 *
 * @author Caspian
 */
@Mapper
public interface ProcessOperationMapper extends BaseMapper<ProcessOperation> {
}
