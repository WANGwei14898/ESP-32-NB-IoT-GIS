package com.urbanflood.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.urbanflood.service.TelemetryService;
import com.urbanflood.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MQTT 消息处理器：解析遥测 JSON 并交由 TelemetryService 落库与告警。
 */
@Slf4j
@Component
public class MqttMessageHandler {

    private final TelemetryService telemetryService;

    public MqttMessageHandler(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    /**
     * 处理一条 MQTT 消息。
     *
     * @param topic   主题，形如 flood/{station_id}/telemetry
     * @param payload 消息体 JSON
     */
    public void handle(String topic, String payload) {
        JsonNode node = JsonUtil.parse(payload);
        if (node == null) {
            log.warn("MQTT 消息解析失败，topic={} payload={}", topic, payload);
            return;
        }
        String stationCode = node.hasNonNull("station_id")
                ? node.get("station_id").asText()
                : extractStationId(topic);
        try {
            telemetryService.processInbound(node, stationCode);
        } catch (Exception e) {
            log.error("处理遥测数据失败，topic={}: {}", topic, e.getMessage(), e);
        }
    }

    private String extractStationId(String topic) {
        // 从 flood/{station_id}/telemetry 中提取 station_id
        if (topic == null) {
            return null;
        }
        String[] parts = topic.split("/");
        return parts.length >= 2 ? parts[1] : null;
    }
}
