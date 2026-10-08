package com.urbanflood.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urbanflood.entity.Device;
import org.apache.ibatis.annotations.Mapper;

/**
 * 监测设备 Mapper。
 */
@Mapper
public interface DeviceMapper extends BaseMapper<Device> {
}
