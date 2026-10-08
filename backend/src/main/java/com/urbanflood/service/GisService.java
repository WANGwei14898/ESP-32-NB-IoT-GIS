package com.urbanflood.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.urbanflood.dto.GisDTO.HeatPoint;
import com.urbanflood.dto.GisDTO.InfluenceCircle;
import com.urbanflood.dto.GisDTO.StationView;
import com.urbanflood.dto.GisDTO.WaterPoint;
import com.urbanflood.dto.TelemetryDTO;
import com.urbanflood.entity.Alert;
import com.urbanflood.entity.Station;
import com.urbanflood.mapper.AlertMapper;
import com.urbanflood.mapper.StationMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GIS 地图数据服务。
 */
@Service
public class GisService {

    /** 积水点判定阈值（米）。 */
    private static final double WATER_POINT_THRESHOLD = 0.5;

    private final StationMapper stationMapper;
    private final AlertMapper alertMapper;
    private final TelemetryService telemetryService;

    public GisService(StationMapper stationMapper, AlertMapper alertMapper, TelemetryService telemetryService) {
        this.stationMapper = stationMapper;
        this.alertMapper = alertMapper;
        this.telemetryService = telemetryService;
    }

    /** 查询站点列表（含坐标、状态、预警等级与最新遥测）。 */
    public List<StationView> stations() {
        List<Station> stations = stationMapper.selectList(null);
        Map<Long, TelemetryDTO> realtime = telemetryService.realtime().stream()
                .collect(Collectors.toMap(TelemetryDTO::getStationId, t -> t, (a, b) -> a));
        Map<Long, String> alertLevels = activeAlertLevels();

        return stations.stream().map(s -> {
            StationView view = new StationView();
            view.setId(s.getId());
            view.setCode(s.getCode());
            view.setName(s.getName());
            view.setLongitude(s.getLongitude());
            view.setLatitude(s.getLatitude());
            view.setAddress(s.getAddress());
            view.setRegion(s.getRegion());
            view.setDeviceId(s.getDeviceId());
            view.setStatus(s.getStatus());
            view.setInstallTime(s.getInstallTime());
            view.setLatest(realtime.get(s.getId()));
            view.setAlertLevel(alertLevels.getOrDefault(s.getId(), "NORMAL"));
            return view;
        }).collect(Collectors.toList());
    }

    /** 查询积水点（最新水位超过阈值的站点）。 */
    public List<WaterPoint> waterPoints() {
        List<WaterPoint> points = new ArrayList<>();
        for (TelemetryDTO t : telemetryService.realtime()) {
            if (t.getWaterLevel() == null || t.getWaterLevel() < WATER_POINT_THRESHOLD) {
                continue;
            }
            Station s = stationMapper.selectById(t.getStationId());
            if (s == null || s.getLongitude() == null || s.getLatitude() == null) {
                continue;
            }
            points.add(new WaterPoint(
                    s.getId(), s.getLongitude(), s.getLatitude(), s.getName(),
                    t.getWaterLevel(), null, LocalDateTime.now()));
        }
        return points;
    }

    /** 查询影响范围圆（存在活动告警的站点）。 */
    public List<InfluenceCircle> influence() {
        List<InfluenceCircle> circles = new ArrayList<>();
        for (StationView view : stations()) {
            String level = view.getAlertLevel();
            if ("NORMAL".equals(level) || view.getLongitude() == null) {
                continue;
            }
            circles.add(new InfluenceCircle(
                    view.getId(), view.getName(), view.getLongitude(), view.getLatitude(),
                    radiusByLevel(level), level));
        }
        return circles;
    }

    /** 查询水位热力数据。 */
    public List<HeatPoint> heatmap() {
        List<HeatPoint> points = new ArrayList<>();
        for (TelemetryDTO t : telemetryService.realtime()) {
            Station s = stationMapper.selectById(t.getStationId());
            if (s == null || s.getLongitude() == null || s.getLatitude() == null) {
                continue;
            }
            points.add(new HeatPoint(s.getLongitude(), s.getLatitude(),
                    t.getWaterLevel() == null ? 0.0 : t.getWaterLevel()));
        }
        return points;
    }

    private double radiusByLevel(String level) {
        return switch (level == null ? "" : level) {
            case "RED" -> 1500.0;
            case "ORANGE" -> 1000.0;
            case "YELLOW" -> 500.0;
            case "BLUE" -> 200.0;
            default -> 0.0;
        };
    }

    private Map<Long, String> activeAlertLevels() {
        List<Alert> alerts = alertMapper.selectList(
                new LambdaQueryWrapper<Alert>().eq(Alert::getStatus, "ACTIVE"));
        Map<Long, String> map = new HashMap<>();
        for (Alert a : alerts) {
            String existing = map.get(a.getStationId());
            if (existing == null || levelRank(a.getLevel()) > levelRank(existing)) {
                map.put(a.getStationId(), a.getLevel());
            }
        }
        return map;
    }

    private int levelRank(String level) {
        return switch (level == null ? "" : level) {
            case "RED" -> 4;
            case "ORANGE" -> 3;
            case "YELLOW" -> 2;
            case "BLUE" -> 1;
            default -> 0;
        };
    }
}
