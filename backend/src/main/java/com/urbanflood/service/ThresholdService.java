package com.urbanflood.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.urbanflood.entity.Threshold;
import com.urbanflood.mapper.ThresholdMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 阈值配置服务。
 */
@Service
public class ThresholdService {

    private final ThresholdMapper thresholdMapper;

    @Value("${mqtt.broker:tcp://localhost:1883}")
    private String mqttBroker;

    @Value("${mqtt.client-id:flood-backend}")
    private String mqttClientId;

    @Value("${mqtt.username:}")
    private String mqttUsername;

    @Value("${mqtt.qos:1}")
    private int mqttQos;

    @Value("${mqtt.topic:flood/+/telemetry}")
    private String mqttTopic;

    @Value("${coap.host:0.0.0.0}")
    private String coapHost;

    @Value("${coap.port:5683}")
    private int coapPort;

    public ThresholdService(ThresholdMapper thresholdMapper) {
        this.thresholdMapper = thresholdMapper;
    }

    /** 查询全部阈值配置。 */
    public List<Threshold> list() {
        return thresholdMapper.selectList(new LambdaQueryWrapper<Threshold>().orderByAsc(Threshold::getId));
    }

    /** 批量保存阈值配置。 */
    @Transactional
    public void update(List<Threshold> thresholds) {
        if (thresholds == null) {
            return;
        }
        for (Threshold t : thresholds) {
            if (t.getId() == null) {
                thresholdMapper.insert(t);
            } else {
                thresholdMapper.updateById(t);
            }
        }
    }

    /** MQTT 配置。 */
    public Map<String, Object> mqttConfig() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("host", mqttBroker);
        map.put("port", extractPort(mqttBroker));
        map.put("clientId", mqttClientId);
        map.put("topic", mqttTopic);
        map.put("username", mqttUsername);
        map.put("qos", mqttQos);
        return map;
    }

    /** CoAP 配置。 */
    public Map<String, Object> coapConfig() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("host", coapHost);
        map.put("port", coapPort);
        map.put("path", "/telemetry/{station_id}");
        map.put("observeEnabled", true);
        return map;
    }

    private int extractPort(String broker) {
        if (broker == null) {
            return 1883;
        }
        try {
            // 形如 tcp://localhost:1883
            String hostPart = broker.substring(broker.lastIndexOf(':') + 1);
            return Integer.parseInt(hostPart);
        } catch (Exception e) {
            return 1883;
        }
    }
}
