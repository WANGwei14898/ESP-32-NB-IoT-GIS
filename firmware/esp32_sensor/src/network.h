/**
 * network.h
 * =========
 * 网络通信层：Wi-Fi 连接（NB-IoT AT 预留）+ MQTT / CoAP 双协议上报。
 * 上报优先级：优先 MQTT，失败时自动降级为 CoAP。
 */
#pragma once

#include <Arduino.h>
#include <WiFi.h>
#include <WiFiUdp.h>
#include <PubSubClient.h>
#include <HardwareSerial.h>

#include "config.h"
#include "secrets.h"

/**
 * 一帧遥测数据的结构化表示。
 * 序列化后的 JSON 字段与后端解析约定严格一致：
 *   station_id / water_level / distance / battery_voltage / battery / signal / timestamp / source
 */
struct TelemetryPayload {
    String stationId;
    float waterLevel;       // 水位（米）
    float distance;         // 超声波实测距离（米）
    float batteryVoltage;   // 电池电压（V）
    int   batteryPercent;   // 电量百分比（%）
    int   rssi;             // Wi-Fi 信号强度（dBm）
};

class NetworkClient {
public:
    NetworkClient();
    ~NetworkClient();

    /** 初始化（Wi-Fi 连接 / NB-IoT 预留）。 */
    bool begin();

    /** 确保网络连接可用（断线自动重连）。 */
    bool ensureConnected();

    /** 发布一帧遥测：优先 MQTT，失败降级 CoAP。返回是否成功。 */
    bool publish(const TelemetryPayload& payload);

    /** 单独走 MQTT 上报。 */
    bool publishMqtt(const TelemetryPayload& payload);

    /** 单独走 CoAP 上报。 */
    bool publishCoap(const TelemetryPayload& payload);

    /** Wi-Fi 是否已连接。 */
    bool wifiConnected();

    // ---------------- NB-IoT（SIM7020）AT 指令预留 ----------------
    /** 向 NB-IoT 模块发送 AT 指令并读取响应（预留接口）。 */
    bool nbiotSendAt(const String& cmd, String& resp, unsigned long timeoutMs = 2000);

private:
    void ensureMqttClient();

    WiFiClient   _tcp;
    PubSubClient _mqtt;
    WiFiUDP      _udp;
    String       _mqttTopic;

    // NB-IoT 预留：独立硬件串口
    HardwareSerial* _nbiotSerial;
};
