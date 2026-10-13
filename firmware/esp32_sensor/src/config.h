/**
 * config.h
 * ========
 * 固件非敏感配置与引脚定义（统一收敛于此文件）。
 * 敏感信息（Wi-Fi 账号密码、服务器 IP、MQTT 账号）请勿写在此处，
 * 一律放到 secrets.h（已加入 .gitignore）。
 */
#pragma once

// ======================= 引脚定义 =======================
// HC-SR04 超声波测距传感器
#define PIN_TRIG            5     // 触发引脚 Trig
#define PIN_ECHO            18    // 回响引脚 Echo

// ======================= 传感器安装参数 =======================
// 传感器安装高度：传感器探头到河道/桥梁底部的垂直距离（米）。
// 水位 = SENSOR_HEIGHT_M - 超声波实测距离。
// 可通过编译宏 SENSOR_HEIGHT_M 或运行时 NVS 覆盖（见 main.cpp）。
#ifndef SENSOR_HEIGHT_M
#define SENSOR_HEIGHT_M     2.0f
#endif

// 超声波最大量程（米），超过视为无效读数
#define SONAR_MAX_RANGE_M   4.0f
// 声速（m/s，随温度变化，此处取 20°C 近似值）
#define SOUND_SPEED_MPS     343.0f

// ======================= 滤波参数 =======================
// 中值滤波窗口大小（奇数），去除偶发噪点
#define MEDIAN_WINDOW_SIZE  5
// 卡尔曼滤波参数（可选启用）
#define ENABLE_KALMAN       0     // 1 = 卡尔曼，0 = 中值滤波
#define KALMAN_Q            0.01f // 过程噪声
#define KALMAN_R            0.1f  // 测量噪声

// ======================= 采集与上报 =======================
// 上报间隔（毫秒）
#ifndef REPORT_INTERVAL_MS
#define REPORT_INTERVAL_MS  30000
#endif
// 传感器单次读取失败的重试次数
#define SENSOR_RETRY_TIMES  3
// 传感器读取失败后进入离线等待的时间（毫秒）
#define SENSOR_FAIL_WAIT_MS 5000

// ======================= 网络 =======================
// 上报协议优先级：1 = 优先 MQTT，失败降级 CoAP；0 = 仅 CoAP
#define PREFER_MQTT         1
// MQTT 重连间隔（毫秒）
#define NETWORK_RETRY_MS    5000

// ======================= 看门狗 =======================
// 任务看门狗超时（毫秒），喂狗周期应远小于该值
#define WDT_TIMEOUT_MS      30000

// ======================= 电池电压测量 =======================
// 电池电压通过分压电阻接到 ADC 引脚（ESP32 ADC1，如 GPIO 34）。
// 电池实际电压 = ADC 测得电压 * 分压比。
#define PIN_BATTERY_ADC     34
#define BATTERY_DIVIDER     2.0f   // 分压比（R1/R2 配置，见硬件接线说明）

