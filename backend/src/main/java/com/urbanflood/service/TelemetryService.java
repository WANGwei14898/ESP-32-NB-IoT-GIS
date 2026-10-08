package com.urbanflood.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.urbanflood.alert.AlertEngine;
import com.urbanflood.dto.TelemetryDTO;
import com.urbanflood.entity.Device;
import com.urbanflood.entity.Station;
import com.urbanflood.entity.Telemetry;
import com.urbanflood.mapper.DeviceMapper;
import com.urbanflood.mapper.StationMapper;
import com.urbanflood.mapper.TelemetryMapper;
import com.urbanflood.util.TimeUtil;
import com.urbanflood.websocket.RealtimePushHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 遥测数据服务：负责遥测数据的入库、设备心跳更新、告警触发与实时推送。
 */
@Slf4j
@Service
public class TelemetryService {

    private final StationMapper stationMapper;
    private final DeviceMapper deviceMapper;
    private final TelemetryMapper telemetryMapper;
    private final AlertEngine alertEngine;
    private final RealtimePushHandler realtimePushHandler;

    public TelemetryService(StationMapper stationMapper,
                            DeviceMapper deviceMapper,
                            TelemetryMapper telemetryMapper,
                            AlertEngine alertEngine,
                            RealtimePushHandler realtimePushHandler) {
        this.stationMapper = stationMapper;
        this.deviceMapper = deviceMapper;
        this.telemetryMapper = telemetryMapper;
        this.alertEngine = alertEngine;
        this.realtimePushHandler = realtimePushHandler;
    }

    /**
     * 处理一条来自 MQTT / CoAP 的遥测数据。
     */
    public TelemetryDTO processInbound(JsonNode node, String stationCode) {
        Station station = resolveStation(stationCode, node);

        Telemetry telemetry = new Telemetry();
        telemetry.setStationId(station.getId());
        telemetry.setWaterLevel(node.hasNonNull("water_level") ? node.get("water_level").asDouble() : null);
        telemetry.setRainfall(node.hasNonNull("rainfall") ? node.get("rainfall").asDouble() : null);
        telemetry.setFlowVelocity(node.hasNonNull("flow_speed") ? node.get("flow_speed").asDouble() : null);
        telemetry.setBattery(node.hasNonNull("battery") ? node.get("battery").asDouble() : null);
        telemetry.setSignal(node.hasNonNull("signal") ? node.get("signal").asDouble() : null);

        LocalDateTime ts = node.hasNonNull("timestamp")
                ? TimeUtil.parseIso(node.get("timestamp").asText())
                : LocalDateTime.now();
        telemetry.setTs(ts == null ? LocalDateTime.now() : ts);

        Device device = findDeviceByStation(station.getId());
        if (device != null) {
            telemetry.setDeviceId(device.getId());
            refreshDeviceHeartbeat(device, telemetry);
        }

        telemetryMapper.insert(telemetry);

        alertEngine.evaluate(telemetry, station);

        TelemetryDTO dto = TelemetryDTO.from(telemetry);
        dto.setStationCode(station.getCode());
        dto.setStationName(station.getName());
        realtimePushHandler.pushTelemetry(dto);

        return dto;
    }

    private Station resolveStation(String stationCode, JsonNode node) {
        Station station = stationMapper.selectOne(
                new LambdaQueryWrapper<Station>().eq(Station::getCode, stationCode));
        if (station != null) {
            return station;
        }
        station = new Station();
        station.setCode(stationCode);
        station.setName(node.hasNonNull("name") ? node.get("name").asText() : ("站点 " + stationCode));
        if (node.hasNonNull("latitude")) {
            station.setLatitude(node.get("latitude").asDouble());
        }
        if (node.hasNonNull("longitude")) {
            station.setLongitude(node.get("longitude").asDouble());
        }
        station.setStatus("ONLINE");
        station.setInstallTime(LocalDateTime.now());
        stationMapper.insert(station);
        log.info("自动注册新站点: {} ({})", stationCode, station.getName());
        return station;
    }

    private Device findDeviceByStation(Long stationId) {
        return deviceMapper.selectOne(
                new LambdaQueryWrapper<Device>().eq(Device::getStationId, stationId));
    }

    private void refreshDeviceHeartbeat(Device device, Telemetry telemetry) {
        device.setLastHeartbeat(LocalDateTime.now());
        device.setBattery(telemetry.getBattery());
        device.setSignal(telemetry.getSignal());
        device.setStatus("ONLINE");
        deviceMapper.updateById(device);
    }
    /** 查询指定站点最新遥测。 */
    public TelemetryDTO latest(Long stationId) {
        Telemetry t = telemetryMapper.selectLatestByStation(stationId);
        return t == null ? null : enrich(TelemetryDTO.from(t), t.getStationId());
    }

    /** 查询时间范围内的历史遥测序列。 */
    public List<TelemetryDTO> history(Long stationId, LocalDateTime start, LocalDateTime end, int limit) {
        List<Telemetry> list = telemetryMapper.selectHistory(stationId, start, end, limit);
        return list.stream().map(t -> enrich(TelemetryDTO.from(t), t.getStationId()))
                .collect(Collectors.toList());
    }

    /** 查询全部站点实时快照。 */
    public List<TelemetryDTO> realtime() {
        List<Telemetry> list = telemetryMapper.selectRealtime();
        return list.stream().map(t -> enrich(TelemetryDTO.from(t), t.getStationId()))
                .collect(Collectors.toList());
    }

    private TelemetryDTO enrich(TelemetryDTO dto, Long stationId) {
        Station station = stationMapper.selectById(stationId);
        if (station != null) {
            dto.setStationCode(station.getCode());
            dto.setStationName(station.getName());
        }
        return dto;
    }
}
