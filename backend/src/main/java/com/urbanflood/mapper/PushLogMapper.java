package com.urbanflood.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urbanflood.entity.PushLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警推送记录 Mapper。
 */
@Mapper
public interface PushLogMapper extends BaseMapper<PushLog> {
}
