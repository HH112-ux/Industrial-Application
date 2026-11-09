package com.zhizao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhizao.entity.Process;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProcessMapper extends BaseMapper<Process> {

    /**
     * 查询同一工艺编号下的最高版本号，包含被逻辑删除的行。
     *
     * <p>版本号一旦使用就不能复用：若最高版本被逻辑删除，唯一键
     * {@code (process_no, version)} 仍然占用该版本号，因此这里必须绕过
     * {@code @TableLogic} 的自动过滤。</p>
     *
     * @param processNo 工艺编号
     * @return 最高版本号，无记录时返回 {@code null}
     */
    @Select("SELECT MAX(version) FROM pr_process WHERE process_no = #{processNo}")
    Integer selectMaxVersionIncludingDeleted(@Param("processNo") String processNo);
}
