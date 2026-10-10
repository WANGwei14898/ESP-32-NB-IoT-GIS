package com.urbanflood.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.urbanflood.entity.Alert;
import com.urbanflood.entity.Station;
import com.urbanflood.entity.Telemetry;
import com.urbanflood.entity.Threshold;
import com.urbanflood.mapper.AlertMapper;
import com.urbanflood.mapper.ThresholdMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 告警引擎：根据阈值对遥测数据进行评估，生成蓝 / 黄 / 橙 / 红四级告警，
 * 写入 alert 表，并通过 Notifier 推送。
 */
@Slf4j
@Component
public class AlertEngine {

    /** 水位阈值编码（按严重程度从高到低排列）。 */
    private static final String[] WATER_CODES = {"WATER_RED", "WATER_ORANGE", "WATER_YELLOW", "WATER_BLUE"};
    /** 雨量阈值编码（按严重程度从高到低排列）。 */
    private static final String[] RAIN_CODES = {"RAIN_RED", "RAIN_ORANGE", "RAIN_YELLOW", "RAIN_BLUE"};
    /** 流速阈值编码（按严重程度从高到低排列）。 */
    private static final String[] FLOW_CODES = {"FLOW_RED", "FLOW_ORANGE", "FLOW_YELLOW", "FLOW_BLUE"};

    private final ThresholdMapper thresholdMapper;
    private final AlertMapper alertMapper;
    private final Notifier notifier;

    public AlertEngine(ThresholdMapper thresholdMapper, AlertMapper alertMapper, Notifier notifier) {
        this.thresholdMapper = thresholdMapper;
        this.alertMapper = alertMapper;
        this.notifier = notifier;
    }

    public void evaluate(Telemetry telemetry, Station station) {
        if (telemetry == null || telemetry.getStationId() == null) {
            return;
        }
        Map<String, Threshold> thresholds = loadThresholds();

        // 水位 / 雨量 / 流速：均按蓝黄橙红四级阈值判断，命中最高等级的越限阈值即触发对应等级告警
        emitByLevel(telemetry, station, thresholds, WATER_CODES, "WATER", "水位超限", telemetry.getWaterLevel(), "m");
        emitByLevel(telemetry, station, thresholds, RAIN_CODES, "RAIN", "降雨量过大", telemetry.getRainfall(), "mm/h");
        emitByLevel(telemetry, station, thresholds, FLOW_CODES, "FLOW", "流速过快", telemetry.getFlowVelocity(), "m/s");

        // 电量（保留版本2的 getThresholdValue() 写法）
        Threshold battery = thresholds.get("BATTERY_LOW");
        if (battery != null && Boolean.TRUE.equals(battery.getEnabled())
                && telemetry.getBattery() != null && telemetry.getBattery() <= battery.getThresholdValue()) {
            emit(telemetry, station, "BATTERY", battery.getLevel(),
                    "电量过低（阈值 " + battery.getThresholdValue() + " %）", telemetry.getBattery(), battery.getThresholdValue());
        }

        // 信号（保留版本2的 getSignalStrength() 和 getThresholdValue() 写法）
        Threshold signal = thresholds.get("SIGNAL_WEAK");
        if (signal != null && Boolean.TRUE.equals(signal.getEnabled())
                && telemetry.getSignalStrength() != null && telemetry.getSignalStrength() <= signal.getThresholdValue()) {
            emit(telemetry, station, "SIGNAL", signal.getLevel(),
                    "信号偏弱（阈值 " + signal.getThresholdValue() + " dBm）", telemetry.getSignalStrength(), signal.getThresholdValue());
        }
    }

    private void emitByLevel(Telemetry t, Station station, Map<String, Threshold> thresholds,
                             String[] codes, String type, String metricText,
                             Double currentValue, String unit) {
        for (String code : codes) {
            Threshold th = thresholds.get(code);
            if (th != null && Boolean.TRUE.equals(th.getEnabled())
                    && currentValue != null && currentValue >= th.getThresholdValue()) {
                emit(t, station, type, th.getLevel(),
                        metricText + "（阈值 " + th.getThresholdValue() + " " + unit + "）", currentValue, th.getThresholdValue());
                return;
            }
        }
    }

    private Map<String, Threshold> loadThresholds() {
        List<Threshold> list = thresholdMapper.selectList(
                new LambdaQueryWrapper<Threshold>().eq(Threshold::getEnabled, true));
        Map<String, Threshold> map = new HashMap<>();
        for (Threshold th : list) {
            map.put(th.getCode(), th);
        }
        return map;
    }

    private void emit(Telemetry t, Station station, String type, String level,
                      String message, Double currentValue, Double thresholdValue) {
        Long count = alertMapper.selectCount(new LambdaQueryWrapper<Alert>()
                .eq(Alert::getStationId, t.getStationId())
                .eq(Alert::getType, type)
                .eq(Alert::getLevel, level)
                .eq(Alert::getStatus, "ACTIVE"));
        if (count != null && count > 0) {
            return;
        }

        Alert alert = new Alert();
        alert.setStationId(t.getStationId());
        alert.setLevel(level);
        alert.setType(type);
        alert.setMessage(message);
        alert.setWaterLevel(currentValue);
        alert.setThreshold(thresholdValue);
        alert.setStatus("ACTIVE");
        alert.setCreateTime(LocalDateTime.now());
        alertMapper.insert(alert);

        log.info("生成告警: 站点={} 等级={} 类型={} 内容={}", t.getStationId(), level, type, message);
        notifier.notify(alert, station);
    }
}