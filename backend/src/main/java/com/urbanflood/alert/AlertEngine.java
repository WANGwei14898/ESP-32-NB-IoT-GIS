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

    private final ThresholdMapper thresholdMapper;
    private final AlertMapper alertMapper;
    private final Notifier notifier;

    public AlertEngine(ThresholdMapper thresholdMapper, AlertMapper alertMapper, Notifier notifier) {
        this.thresholdMapper = thresholdMapper;
        this.alertMapper = alertMapper;
        this.notifier = notifier;
    }

    /**
     * 评估一条遥测数据，必要时生成告警。
     *
     * @param telemetry 遥测数据
     * @param station   关联站点（可能为 null）
     */
    public void evaluate(Telemetry telemetry, Station station) {
        if (telemetry == null || telemetry.getStationId() == null) {
            return;
        }
        Map<String, Threshold> thresholds = loadThresholds();

        // 水位：取最高等级的越限阈值
        Threshold waterTrigger = null;
        for (String code : WATER_CODES) {
            Threshold th = thresholds.get(code);
            if (th != null && Boolean.TRUE.equals(th.getEnabled())
                    && telemetry.getWaterLevel() != null && telemetry.getWaterLevel() >= th.getValue()) {
                waterTrigger = th;
                break;
            }
        }
        if (waterTrigger != null) {
            emit(telemetry, station, "WATER", waterTrigger.getLevel(),
                    "水位超限（阈值 " + waterTrigger.getValue() + " m）", telemetry.getWaterLevel(), waterTrigger.getValue());
        }

        // 雨量
        Threshold rain = thresholds.get("RAIN_HEAVY");
        if (rain != null && Boolean.TRUE.equals(rain.getEnabled())
                && telemetry.getRainfall() != null && telemetry.getRainfall() >= rain.getValue()) {
            emit(telemetry, station, "RAIN", rain.getLevel(),
                    "降雨量过大（阈值 " + rain.getValue() + " mm/h）", telemetry.getRainfall(), rain.getValue());
        }

        // 流速
        Threshold flow = thresholds.get("FLOW_HIGH");
        if (flow != null && Boolean.TRUE.equals(flow.getEnabled())
                && telemetry.getFlowVelocity() != null && telemetry.getFlowVelocity() >= flow.getValue()) {
            emit(telemetry, station, "FLOW", flow.getLevel(),
                    "流速过快（阈值 " + flow.getValue() + " m/s）", telemetry.getFlowVelocity(), flow.getValue());
        }

        // 电量
        Threshold battery = thresholds.get("BATTERY_LOW");
        if (battery != null && Boolean.TRUE.equals(battery.getEnabled())
                && telemetry.getBattery() != null && telemetry.getBattery() <= battery.getValue()) {
            emit(telemetry, station, "BATTERY", battery.getLevel(),
                    "电量过低（阈值 " + battery.getValue() + " %）", telemetry.getBattery(), battery.getValue());
        }

        // 信号
        Threshold signal = thresholds.get("SIGNAL_WEAK");
        if (signal != null && Boolean.TRUE.equals(signal.getEnabled())
                && telemetry.getSignal() != null && telemetry.getSignal() <= signal.getValue()) {
            emit(telemetry, station, "SIGNAL", signal.getLevel(),
                    "信号偏弱（阈值 " + signal.getValue() + " dBm）", telemetry.getSignal(), signal.getValue());
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

    /** 生成并落库告警（含去重与推送）。 */
    private void emit(Telemetry t, Station station, String type, String level,
                      String message, Double currentValue, Double thresholdValue) {
        // 去重：同一站点、同一类型、同一等级的未处理告警已存在则跳过
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
