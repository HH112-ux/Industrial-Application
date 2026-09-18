package com.zhizao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 业务编号序列（数据库序列表）。
 */
@TableName("sys_sequence")
public class Sequence {

    @TableId(type = IdType.INPUT)
    private String seqName;
    private Long currentValue;

    public String getSeqName() { return seqName; }
    public void setSeqName(String seqName) { this.seqName = seqName; }
    public Long getCurrentValue() { return currentValue; }
    public void setCurrentValue(Long currentValue) { this.currentValue = currentValue; }
}
