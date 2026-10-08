package com.urbanflood.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urbanflood.entity.Alert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警 Mapper。
 */
@Mapper
public interface AlertMapper extends BaseMapper<Alert> {
}
