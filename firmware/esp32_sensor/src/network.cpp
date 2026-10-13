/**
 * network.cpp
 * ===========
 * 网络通信层实现。
 *   - MQTT：PubSubClient，主题 flood/{station_id}/telemetry
 *   - CoAP：手写最小 CoAP 客户端（UDP，CON + PUT，Content-Format 50）
 *     资源路径 coap://{host}:{port}/telemetry/{station_id}
 */
#include "network.h"
#include <time.h>

static void mqttCallbackDummy(char* topic, byte* payload, unsigned int length) {
    (void)topic; (void)payload; (void)length;
}

NetworkClient::NetworkClient()
    : _mqtt(_tcp), _nbiotSerial(nullptr) {
    _mqttTopic = String("flood/") + STATION_ID + "/telemetry";
}

NetworkClient::~NetworkClient() {
    if (_nbiotSerial) {
        _nbiotSerial->end();
        delete _nbiotSerial;
        _nbiotSerial = nullptr;
    }
}

// ======================= 初始化 / 连接 =======================

bool NetworkClient::begin() {
#if ENABLE_NBIOT
    _nbiotSerial = new HardwareSerial(2);
    _nbiotSerial->begin(115200, SERIAL_8N1, NBIOT_RX_PIN, NBIOT_TX_PIN);
    Serial.println("[network] NB-IoT 模式已启用（预留接口）");
    return true;
#else
    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    Serial.printf("[network] 连接 Wi-Fi: %s\n", WIFI_SSID);
    return ensureConnected();
#endif
}

bool NetworkClient::wifiConnected() {
    return WiFi.status() == WL_CONNECTED;
}

bool NetworkClient::ensureConnected() {
#if ENABLE_NBIOT
    return true;
#else
    if (wifiConnected()) {
        return true;
    }
    unsigned long start = millis();
    while (!wifiConnected()) {
        if (millis() - start > 15000) {
            Serial.println("[network] Wi-Fi 连接超时");
            return false;
        }
        delay(500);
        Serial.print(".");
    }
    Serial.println();
    Serial.printf("[network] Wi-Fi 已连接，IP=%s\n", WiFi.localIP().toString().c_str());
    return true;
#endif
}

// ======================= 统一上报入口 =======================

bool NetworkClient::publish(const TelemetryPayload& payload) {
#if PREFER_MQTT
    if (publishMqtt(payload)) {
        return true;
    }
    Serial.println("[network] MQTT 上报失败，降级为 CoAP");
    return publishCoap(payload);
#else
    return publishCoap(payload);
#endif
}

// ======================= JSON 序列化 =======================

/** 生成当前时间的 ISO-8601 字符串（依赖 NTP 同步）。 */
static String nowTimestamp() {
    time_t now = time(nullptr);
    struct tm tmv;
    localtime_r(&now, &tmv);
    char buf[32];
    strftime(buf, sizeof(buf), "%Y-%m-%dT%H:%M:%S%z", &tmv);
    return String(buf);
}

/** 字段命名与后端 TelemetryService.processInbound 解析约定一致。 */
static String buildPayloadJson(const TelemetryPayload& p) {
    String j;
    j.reserve(256);
    j += "{\"station_id\":\"";
    j += p.stationId;
    j += "\",\"water_level\":";
    j += String(p.waterLevel, 3);
    j += ",\"distance\":";
    j += String(p.distance, 3);
    j += ",\"battery_voltage\":";
    j += String(p.batteryVoltage, 2);
    j += ",\"battery\":";
    j += String(p.batteryPercent);
    j += ",\"signal\":";
    j += String(p.rssi);
    j += ",\"timestamp\":\"";
    j += nowTimestamp();
    j += "\",\"source\":\"esp32\"}";
    return j;
}

// ======================= MQTT 上报 =======================

void NetworkClient::ensureMqttClient() {
    static bool configured = false;
    if (!configured) {
        _mqtt.setServer(SERVER_HOST, MQTT_PORT);
        _mqtt.setCallback(mqttCallbackDummy);
        _mqtt.setBufferSize(512);
        configured = true;
    }
}

bool NetworkClient::publishMqtt(const TelemetryPayload& payload) {
    ensureMqttClient();

    String clientId = String("esp32-") + STATION_ID;
    if (!_mqtt.connected()) {
        if (!ensureConnected()) {
            return false;
        }
        if (strlen(MQTT_USERNAME) > 0) {
            _mqtt.connect(clientId.c_str(), MQTT_USERNAME, MQTT_PASSWORD);
        } else {
            _mqtt.connect(clientId.c_str());
        }
        if (!_mqtt.connected()) {
            Serial.println("[network] MQTT 连接失败");
            return false;
        }
        Serial.println("[network] MQTT 已连接");
    }

    String json = buildPayloadJson(payload);
    bool ok = _mqtt.publish(_mqttTopic.c_str(), json.c_str());
    if (ok) {
        Serial.printf("[network] MQTT 已上报 -> %s\n", _mqttTopic.c_str());
    }
    return ok;
}


// ======================= CoAP 上报（最小实现） =======================

/**
 * 构造 CoAP CON PUT 报文到 buffer。
 * 头部：Ver=1, Type=CON(0), TKL=0；Code=PUT(0.03)。
 * Options：Uri-Path(11) 两段、Content-Format(12)=application/json。
 */
static size_t buildCoapPut(uint8_t* buf, const String& stationId,
                           const String& json, uint16_t msgId) {
    buf[0] = 0x40;              // Ver=01, Type=CON(00), TKL=0000
    buf[1] = 0x03;              // Code: PUT (0.03)
    buf[2] = (uint8_t)((msgId >> 8) & 0xFF);
    buf[3] = (uint8_t)(msgId & 0xFF);
    size_t pos = 4;

    buf[pos++] = 0xB9;          // Uri-Path: "telemetry"（num 11, delta 11, len 9）
    memcpy(&buf[pos], "telemetry", 9);
    pos += 9;

    uint8_t sidLen = (uint8_t)stationId.length();
    buf[pos++] = sidLen;        // Uri-Path: {station_id}（num 11, delta 0）
    memcpy(&buf[pos], stationId.c_str(), sidLen);
    pos += sidLen;

    buf[pos++] = 0x11;          // Content-Format（num 12, delta 1, len 1）
    buf[pos++] = 50;            // 50 = application/json

    buf[pos++] = 0xFF;          // payload 分隔符
    memcpy(&buf[pos], json.c_str(), json.length());
    pos += json.length();
    return pos;
}

bool NetworkClient::publishCoap(const TelemetryPayload& payload) {
    if (!ensureConnected()) {
        return false;
    }

    String json = buildPayloadJson(payload);
    uint8_t buf[512];
    static uint16_t msgId = 0x1000;

    const int maxRetry = 2;
    for (int attempt = 0; attempt <= maxRetry; attempt++) {
        uint16_t mid = msgId++;
        size_t len = buildCoapPut(buf, payload.stationId, json, mid);

        _udp.beginPacket(SERVER_HOST, COAP_PORT);
        _udp.write(buf, len);
        if (!_udp.endPacket()) {
            Serial.println("[network] CoAP 发送失败");
            return false;
        }

        unsigned long deadline = millis() + 2000;
        while (millis() < deadline) {
            int size = _udp.parsePacket();
            if (size > 0) {
                uint8_t resp[64];
                int n = _udp.read(resp, sizeof(resp));
                if (n >= 4 && (resp[0] >> 4) == 0x6) {  // ACK 消息（Type=10）
                    Serial.println("[network] CoAP 已上报（收到 ACK）");
                    return true;
                }
            }
            delay(10);
        }
        Serial.printf("[network] CoAP 等待 ACK 超时（第 %d 次）\n", attempt + 1);
    }
    return false;
}

// ======================= NB-IoT AT 预留 =======================

bool NetworkClient::nbiotSendAt(const String& cmd, String& resp, unsigned long timeoutMs) {
    if (!_nbiotSerial) {
        return false;
    }
    _nbiotSerial->print(cmd);
    _nbiotSerial->print("\r\n");
    unsigned long start = millis();
    resp = "";
    while (millis() - start < timeoutMs) {
        while (_nbiotSerial->available()) {
            char c = (char)_nbiotSerial->read();
            resp += c;
        }
        if (resp.indexOf("OK") >= 0 || resp.indexOf("ERROR") >= 0) {
            return resp.indexOf("OK") >= 0;
        }
        delay(10);
    }
    return false;
}
