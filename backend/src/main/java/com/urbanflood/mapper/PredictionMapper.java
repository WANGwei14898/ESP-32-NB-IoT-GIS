package com.urbanflood.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urbanflood.entity.Prediction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 水位预测 Mapper。
 */
@Mapper
public interface PredictionMapper extends BaseMapper<Prediction> {

    /** 查询指定站点最新一条预测结果。 */
    @Select("SELECT * FROM prediction WHERE station_id = #{stationId} ORDER BY predict_time DESC LIMIT 1")
    Prediction selectLatestByStation(@Param("stationId") Long stationId);
}
