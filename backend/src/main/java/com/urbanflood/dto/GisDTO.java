package com.urbanflood.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * GIS 相关数据传输对象集合，包含地图图层所需的各种视图结构。
 */
public class GisDTO {

    /**
     * 站点视图（含坐标、状态、预警等级与最新遥测）。
     */
    @Data
    @NoArgsConstructor
    public static class StationView {
        private Long id;
        private String code;
        private String name;
        private Double longitude;
        private Double latitude;
        private String address;
        private String region;
        private Long deviceId;
        private String status;
        private LocalDateTime installTime;
        private String alertLevel;
        private TelemetryDTO latest;
    }

    /**
     * 积水点。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaterPoint {
        private Long id;
        private Double longitude;
        private Double latitude;
        private String name;
        private Double depth;
        private Double area;
        private LocalDateTime reportTime;
    }

    /**
     * 影响范围圆。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InfluenceCircle {
        private Long stationId;
        private String stationName;
        private Double longitude;
        private Double latitude;
        private Double radius;
        private String level;
    }

    /**
     * 水位热力点。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeatPoint {
        private Double longitude;
        private Double latitude;
        private Double value;
    }
}
