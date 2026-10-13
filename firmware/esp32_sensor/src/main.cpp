/**
 * main.cpp
 * ========
 * ESP32 水位监测固件主程序。
 *
 * 主流程：
 *   1. 初始化串口、看门狗、传感器、网络
 *   2. NTP 同步时间（保证上报时间戳准确）
 *   3. 循环：测距 -> 滤波 -> 计算水位 -> 读取电池电压 -> 上报（MQTT/CoAP）-> 喂狗
 *
 * 稳定性设计：
 *   - 任务看门狗（esp_task_wdt）防死锁
 *   - 传感器读取失败自动重试并进入等待
 *   - 网络断线自动重连（network.ensureConnected）
 */
#include <Arduino.h>
#include <esp_task_wdt.h>
#include <Preferences.h>
#include <time.h>

#include "config.h"
#include "secrets.h"
#include "sensor.h"
#include "network.h"

// ---------------- 全局对象 ----------------
static UltrasonicSensor   g_sensor(PIN_TRIG, PIN_ECHO, SONAR_MAX_RANGE_M);
static WaterLevelMonitor  g_monitor(g_sensor, SENSOR_HEIGHT_M);
static NetworkClient      g_network;
static Preferences        g_prefs;

// ---------------- NTP 时间同步 ----------------
static void setupNtp() {
    // 东八区，无夏令时；使用阿里云与标准 NTP 服务器
    configTime(8 * 3600, 0, "ntp.aliyun.com", "pool.ntp.org");
    Serial.println("[main] 正在同步 NTP 时间...");
    unsigned long start = millis();
    while (time(nullptr) < 1000000000 && millis() - start < 10000) {
        delay(200);
    }
    if (time(nullptr) >= 1000000000) {
        Serial.println("[main] NTP 时间同步成功");
    } else {
        Serial.println("[main] NTP 同步失败，使用系统默认时间");
    }
}

// ---------------- 电池电压 ----------------
static float readBatteryVoltage() {
    int mv = analogReadMilliVolts(PIN_BATTERY_ADC);
    return (mv / 1000.0f) * BATTERY_DIVIDER;
}

static int batteryPercent(float voltage) {
    // 单节锂电池线性映射：3.3V=0%，4.2V=100%
    int pct = (int)((voltage - 3.3f) / (4.2f - 3.3f) * 100.0f);
    return constrain(pct, 0, 100);
}

// ---------------- 传感器安装高度（NVS 可配置） ----------------
static float loadSensorHeight() {
    g_prefs.begin("flood", false);
    float h = g_prefs.getFloat("sensor_h", SENSOR_HEIGHT_M);
    g_prefs.end();
    return h;
}

// ---------------- 初始化 ----------------
void setup() {
    Serial.begin(115200);
    Serial.println();
    Serial.println("========================================");
    Serial.println("  城市内涝监测 - ESP32 水位监测固件");
    Serial.println("========================================");

    // 任务看门狗：超时自动重启，喂狗见 loop()
    esp_task_wdt_init(WDT_TIMEOUT_MS / 1000, true);
    esp_task_wdt_add(nullptr);

    // 传感器
    g_sensor.begin();
    float h = loadSensorHeight();
    g_monitor.setSensorHeight(h);
    Serial.printf("[main] 传感器安装高度 = %.2f m\n", h);

    // 网络 + NTP
    g_network.begin();
    setupNtp();

    Serial.printf("[main] 站点编号 = %s，上报间隔 = %d ms\n", STATION_ID, REPORT_INTERVAL_MS);
}

// ---------------- 主循环 ----------------
void loop() {
    // 喂狗
    esp_task_wdt_reset();

    // 1. 采集水位（含滤波与重试）
    float level = g_monitor.readWaterLevel();
    if (isnan(level)) {
        Serial.println("[main] 传感器读取失败，进入等待重试");
        delay(SENSOR_FAIL_WAIT_MS);
        return;
    }

    // 2. 组装上报数据
    float voltage = readBatteryVoltage();

    TelemetryPayload payload;
    payload.stationId       = STATION_ID;
    payload.waterLevel      = level;
    payload.distance        = g_monitor.lastDistance();
    payload.batteryVoltage  = voltage;
    payload.batteryPercent  = batteryPercent(voltage);
    payload.rssi            = WiFi.RSSI();

    Serial.printf("[main] 水位=%.3f m  距离=%.3f m  电池=%.2f V(%d%%)  RSSI=%d dBm\n",
                  level, payload.distance, voltage, payload.batteryPercent, payload.rssi);

    // 3. 上报（优先 MQTT，失败自动降级 CoAP）
    if (!g_network.publish(payload)) {
        Serial.println("[main] 本轮上报失败，将在下个周期重试");
    }

    // 4. 等待下个上报周期（期间保持喂狗）
    unsigned long next = millis() + REPORT_INTERVAL_MS;
    while (millis() < next) {
        esp_task_wdt_reset();
        delay(100);
    }
}
