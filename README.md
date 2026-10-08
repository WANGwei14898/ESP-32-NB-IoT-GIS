# ESP-32-NB-IoT-GIS
基于NB-IoT模拟与GIS的城市内涝监测预警系统

## 项目结构

```
ESP-32-NB-IoT-GIS/
├── docker-compose.yml          # 基础设施编排（EMQX MQTT Broker）
├── simulation/                 # 监测站点模拟器（阶段 2）
│   ├── config.py               # 站点与上报配置
│   ├── data_generator.py       # 遥测数据生成器
│   ├── station_simulator.py    # 单站点模拟器（MQTT / CoAP）
│   └── run_all.py              # 一键启动所有站点
├── coap_server/
│   └── coap_server.py          # CoAP 服务端（阶段 3）
├── scripts/
│   ├── test_mqtt.py            # MQTT 连通性测试
│   └── test_coap.py            # CoAP 连通性测试
└── urban-flood-monitoring/     # 参考项目
```

## 环境要求

- Python 3.8+
- Docker / Docker Compose（用于运行 EMQX）

## 安装依赖

```bash
pip install -r simulation/requirements.txt
```

## 启动说明

### 1. 启动 MQTT Broker（EMQX）

```bash
docker compose up -d
```

启动后：
- MQTT 端口：`1883`
- WebSocket 端口：`8083`
- 管理控制台：http://localhost:18083 （默认账号 `admin` / `public`）

### 2. 启动 CoAP 服务端

```bash
python coap_server/coap_server.py
```

服务监听 `coap://localhost:5683/telemetry/{station_id}`。

如需将 CoAP 收到的数据转发到 MQTT，可设置环境变量：

```bash
# Windows (PowerShell)
$env:FORWARD_TO_MQTT="true"; python coap_server/coap_server.py
# Linux / macOS
FORWARD_TO_MQTT=true python coap_server/coap_server.py
```

### 3. 启动监测站点模拟器

```bash
# 持续循环上报（默认 MQTT）
python simulation/run_all.py

# 仅启动前 5 个站点
python simulation/run_all.py --count 5

# 所有站点仅上报一次
python simulation/run_all.py --once
```

切换上报协议：复制 `simulation/.env.example` 为 `simulation/.env`，设置 `PROTOCOL=coap` 即可。

## 连通性测试

```bash
# 订阅并打印所有站点遥测数据（验证模拟器 -> MQTT）
python scripts/test_mqtt.py

# 发布一条测试消息（验证 MQTT Broker）
python scripts/test_mqtt.py --publish

# 向 CoAP 服务端发送一条测试数据（验证 CoAP 服务端）
python scripts/test_coap.py --station S001
```

## 上报主题与资源

- MQTT 主题：`flood/{station_id}/telemetry`
- CoAP 资源：`coap://localhost/telemetry/{station_id}`

## 上报数据格式

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
  "longitude": 113.357
}
```
