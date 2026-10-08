package com.urbanflood.mqtt;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * MQTT 订阅器：连接 Broker 并订阅遥测主题 flood/+/telemetry，
 * 收到消息后交给 MqttMessageHandler 处理。
 */
@Slf4j
@Component
public class MqttSubscriber implements MqttCallback {

    private final MqttClient mqttClient;
    private final MqttConnectOptions options;
    private final MqttMessageHandler messageHandler;

    @Value("${mqtt.topic:flood/+/telemetry}")
    private String topic;

    @Value("${mqtt.qos:1}")
    private int qos;

    public MqttSubscriber(MqttClient mqttClient,
                          MqttConnectOptions options,
                          MqttMessageHandler messageHandler) {
        this.mqttClient = mqttClient;
        this.options = options;
        this.messageHandler = messageHandler;
    }

    @PostConstruct
    public void start() {
        try {
            mqttClient.setCallback(this);
            mqttClient.connect(options);
            mqttClient.subscribe(topic, qos);
            log.info("MQTT 已连接并订阅主题: {}", topic);
        } catch (Exception e) {
            log.error("MQTT 连接/订阅失败: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void stop() {
        try {
            if (mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
            mqttClient.close();
            log.info("MQTT 连接已关闭");
        } catch (Exception e) {
            log.warn("MQTT 关闭异常: {}", e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT 连接丢失: {}", cause == null ? "unknown" : cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        messageHandler.handle(topic, payload);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 订阅端无需处理
    }
}
