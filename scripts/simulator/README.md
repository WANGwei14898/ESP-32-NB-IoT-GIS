# 监测站点模拟器（scripts/simulator）

用于在没有真实 ESP32 硬件时，模拟生成监测站点的水位/雨量/流速等遥测数据，并通过 MQTT 或 CoAP 上报到后端。与真实硬件固件（`firmware/esp32_sensor/`）共用同一套后端协议。

## 与真实硬件的区别

上报 JSON 中的 `source` 字段标识数据来源：

| source | 含义 | 说明 |
|--------|------|------|
| `esp32` | 真实硬件数据 | 由 ESP32 固件上报，正常写入数据库并触发告警 |
| `simulator` | 模拟数据 | 由本模拟器上报，是否触发告警由后端 `DATA_MODE` 配置决定 |

## 安装依赖

```bash
pip install -r requirements.txt
```

## 启动方式

```bash
# 启动全部站点，持续循环上报（默认 MQTT）
python run_all.py

# 仅启动前 5 个站点
python run_all.py --count 5

# 所有站点仅上报一次后退出（便于验证）
python run_all.py --once

# 单站点模拟（默认第一个站点）
python station_simulator.py --station S001 --once
```

## 配置（.env）

复制 `.env.example` 为 `.env` 并按需修改：

```bash
cp .env.example .env
```

关键配置项：

```ini
# 上报协议：mqtt 或 coap
PROTOCOL=mqtt

# 数据来源标识（固定为 simulator）
SOURCE=simulator

# 模拟站点编号：
#   留空           -> 使用默认站点编号 S001~S010
#   sim_station_01 -> 模拟专用编号，避免与真实硬件冲突
#   S001           -> 与真实硬件共用同一站点编号，模拟真实环境
SIM_STATION_ID=
```

## 上报格式

MQTT 主题 `flood/{station_id}/telemetry`，CoAP 资源 `coap://{host}:5683/telemetry/{station_id}`：

```json
{
  "station_id": "S001",
  "timestamp": "2026-10-08T12:00:00+08:00",
  "water_level": 0.51,
  "rainfall": 3.2,
  "flow_speed": 0.88,
  "battery": 99.9,
  "signal": -78.2,
  "latitude": 23.1291,
  "longitude": 113.357,
  "source": "simulator"
}
```

## 文件说明

| 文件 | 说明 |
|------|------|
| `config.py` | MQTT/CoAP 地址、站点列表、阈值、source、站点编号配置 |
| `data_generator.py` | 随机数据生成器（可复现随机种子） |
| `station_simulator.py` | 单站点模拟器（MQTT/CoAP 双协议上报） |
| `run_all.py` | 多站点多线程一键启动 |
| `.env.example` | 环境变量模板 |
