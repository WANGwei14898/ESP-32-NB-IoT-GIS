package com.urbanflood.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urbanflood.entity.Telemetry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 遥测数据 Mapper。
 */
@Mapper
public interface TelemetryMapper extends BaseMapper<Telemetry> {

    /** 查询指定站点最新一条遥测。 */
    @Select("SELECT * FROM telemetry WHERE station_id = #{stationId} ORDER BY ts DESC LIMIT 1")
    Telemetry selectLatestByStation(@Param("stationId") Long stationId);

    /** 查询时间范围内的历史遥测序列（按时间升序）。 */
    @Select("SELECT * FROM telemetry WHERE station_id = #{stationId} "
            + "AND ts >= #{start} AND ts <= #{end} ORDER BY ts ASC LIMIT #{limit}")
    List<Telemetry> selectHistory(@Param("stationId") Long stationId,
                                  @Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end,
                                  @Param("limit") int limit);

    /** 查询全部站点实时快照（每个站点最新一条）。 */
    @Select("SELECT t.* FROM telemetry t INNER JOIN "
            + "(SELECT station_id, MAX(ts) AS max_ts FROM telemetry GROUP BY station_id) m "
            + "ON t.station_id = m.station_id AND t.ts = m.max_ts ORDER BY t.station_id")
    List<Telemetry> selectRealtime();

    /** 查询指定站点最近 N 条遥测（用于预测服务输入）。 */
    @Select("SELECT * FROM telemetry WHERE station_id = #{stationId} ORDER BY ts DESC LIMIT #{limit}")
    List<Telemetry> selectRecent(@Param("stationId") Long stationId, @Param("limit") int limit);
}
