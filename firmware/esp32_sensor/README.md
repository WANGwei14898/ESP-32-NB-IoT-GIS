# ESP32 水位监测固件

基于 ESP32 + HC-SR04 超声波测距的城市内涝水位监测固件，通过 MQTT / CoAP 双协议向后端上报数据。

## 目录结构

```
esp32_sensor/
├── platformio.ini          # PlatformIO 工程配置
├── .gitignore              # 忽略 secrets.h 等敏感文件
└── src/
    ├── config.h            # 非敏感配置与引脚定义
    ├── secrets.h           # 敏感信息（需从 secrets.h.example 复制）
    ├── secrets.h.example   # 敏感信息模板
    ├── sensor.h            # HC-SR04 驱动 + 滤波 + 水位计算
    ├── network.h/.cpp      # Wi-Fi / NB-IoT + MQTT / CoAP
    └── main.cpp            # 主程序（看门狗 + 采集 + 上报）
```

## 硬件接线

| 元件 | 引脚 | 说明 |
|------|------|------|
| HC-SR04 Trig | GPIO 5 | 触发引脚 |
| HC-SR04 Echo | GPIO 18 | 回响引脚 |
| HC-SR04 VCC | 5V | 供电 |
| HC-SR04 GND | GND | 地 |
| 电池分压采样 | GPIO 34 | ADC1，经分压电阻接到电池 |

> 电池电压测量需外接分压电阻（如 100k / 100k），`BATTERY_DIVIDER` 需与实际分压比一致。

## 水位计算

传感器安装在桥梁/支架上**向下**测距：

```
水位 = 传感器距底部高度 - 超声波实测距离
```

- 「传感器距底部高度」在 `config.h` 的 `SENSOR_HEIGHT_M` 中配置（默认 2.0m），
  也可运行时通过 NVS 键 `sensor_h` 覆盖（无需重新编译）。
- 测距结果先经中值滤波（或卡尔曼滤波）去除噪点。

## 配置

1. 复制敏感信息模板：
   ```bash
   cp src/secrets.h.example src/secrets.h
   ```
2. 编辑 `src/secrets.h`，填写 Wi-Fi 账号密码、服务器 IP、MQTT 账号、站点编号。
3. 如需调整引脚 / 上报间隔 / 滤波参数，编辑 `src/config.h`。

## 编译与烧录

```bash
pio run                 # 编译
pio run -t upload       # 烧录（需连接设备）
pio device monitor      # 查看串口日志
```

## 上报协议

- **MQTT**（优先）：主题 `flood/{station_id}/telemetry`
- **CoAP**（MQTT 失败时降级）：`PUT coap://{host}:5683/telemetry/{station_id}`

## 上报 JSON 格式

```json
{
  "station_id": "S001",
  "water_level": 0.51,
  "distance": 1.49,
  "battery_voltage": 3.72,
  "battery": 47,
  "signal": -55,
  "timestamp": "2026-10-13T12:00:00+0800",
  "source": "esp32"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| station_id | string | 站点编号，需与后端 station.code 一致 |
| water_level | number | 水位（米） |
| distance | number | 超声波实测距离（米） |
| battery_voltage | number | 电池电压（V） |
| battery | number | 电量百分比（%） |
| signal | number | Wi-Fi 信号强度（dBm） |
| timestamp | string | ISO-8601 采集时间 |
| source | string | 固定为 `esp32`（标识真实硬件数据） |

## 稳定性机制

- **看门狗**：任务看门狗（30s 超时自动重启）
- **断线重连**：Wi-Fi / MQTT 自动重连
- **传感器重试**：单次采集失败重试 3 次，仍失败进入等待
- **协议降级**：MQTT 失败自动切换 CoAP
