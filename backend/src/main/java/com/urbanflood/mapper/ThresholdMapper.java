package com.urbanflood.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urbanflood.entity.Threshold;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警阈值 Mapper。
 */
@Mapper
public interface ThresholdMapper extends BaseMapper<Threshold> {
}
