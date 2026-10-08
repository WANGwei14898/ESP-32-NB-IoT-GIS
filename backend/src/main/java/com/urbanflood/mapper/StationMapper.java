package com.urbanflood.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urbanflood.entity.Station;
import org.apache.ibatis.annotations.Mapper;

/**
 * 监测站点 Mapper。
 */
@Mapper
public interface StationMapper extends BaseMapper<Station> {
}
