package com.urbanflood.config;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MQTT 配置：创建 Paho MQTT 客户端与连接参数。
 */
@Configuration
public class MqttConfig {

    @Value("${mqtt.broker:tcp://localhost:1883}")
    private String broker;

    @Value("${mqtt.client-id:flood-backend}")
    private String clientId;

    @Value("${mqtt.username:}")
    private String username;

    @Value("${mqtt.password:}")
    private String password;

    @Value("${mqtt.clean-session:true}")
    private boolean cleanSession;

    @Value("${mqtt.keep-alive:60}")
    private int keepAlive;

    @Bean
    public MqttClient mqttClient() throws MqttException {
        return new MqttClient(broker, clientId, new MemoryPersistence());
    }

    @Bean
    public MqttConnectOptions mqttConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(cleanSession);
        options.setKeepAliveInterval(keepAlive);
        options.setAutomaticReconnect(true);
        if (username != null && !username.isBlank()) {
            options.setUserName(username);
            options.setPassword(password == null ? new char[0] : password.toCharArray());
        }
        return options;
    }
}
