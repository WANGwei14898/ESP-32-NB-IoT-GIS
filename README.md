# ESP-32-NB-IoT-GIS

基于 NB-IoT 模拟与 GIS 的城市内涝监测预警系统。

> 本项目 **完全无需 Docker**，所有组件（MQTT Broker、后端、模拟器、CoAP 服务端、MySQL）均可手动安装并逐个启动。

## 项目结构

```
ESP-32-NB-IoT-GIS/
├── mqtt_broker/                # 轻量 MQTT Broker（纯 Python，替代 EMQX）
│   ├── broker.py               # MQTT Broker 启动脚本
│   └── requirements.txt        # amqtt 依赖
├── backend/                    # 后端 Spring Boot 云平台（阶段 4）
│   ├── pom.xml
│   ├── sql/init.sql            # 建库建表脚本（含种子数据）
│   └── src/main/...            # Java 源码
├── simulation/                 # 监测站点模拟器（阶段 2）
│   ├── config.py               # 站点与上报配置
│   ├── data_generator.py       # 遥测数据生成器
│   ├── station_simulator.py    # 单站点模拟器（MQTT / CoAP）
│   └── run_all.py              # 一键启动所有站点
├── coap_server/
│   └── coap_server.py          # CoAP 服务端（阶段 3，可选）
├── scripts/
│   ├── test_mqtt.py            # MQTT 连通性测试
│   └── test_coap.py            # CoAP 连通性测试
└── urban-flood-monitoring/     # 参考项目（含前端等）
```

## 环境要求

| 组件 | 要求 |
|------|------|
| Python | 3.10+（运行 MQTT Broker / 模拟器 / CoAP 服务端） |
| JDK | 17+（运行后端 Spring Boot） |
| Maven | 3.6+（构建后端） |
| MySQL | 8.0+（数据存储） |

## 安装依赖

```bash
# MQTT Broker（纯 Python，替代 Docker 版 EMQX）
pip install -r mqtt_broker/requirements.txt

# 监测站点模拟器
pip install -r simulation/requirements.txt
```

## 手动启动步骤

按以下顺序依次启动各组件：

### 1. 初始化 MySQL 数据库

安装并启动 MySQL 8.0（无需 Docker），然后执行初始化脚本：

```bash
mysql -uroot -p < backend/sql/init.sql
```

> 脚本会自动创建数据库 `flood_monitor`、八张表（含按月分区的 telemetry 表）以及站点/设备/阈值/账号等种子数据。

接着修改 `backend/src/main/resources/application.yml` 中的数据库连接（默认 `root` / `root`）为你本机的 MySQL 账号密码：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/flood_monitor?...
    username: root      # 改成你的账号
    password: root      # 改成你的密码
```

### 2. 启动 MQTT Broker（无需 Docker）

```bash
python mqtt_broker/broker.py
```

启动后监听 `localhost:1883`，默认开启匿名访问。

> 备选：若你更习惯使用系统级消息中间件，也可手动安装 [Mosquitto](https://mosquitto.org/download/) 或 EMQX 的本地安装包，监听 `1883` 端口即可，其余步骤不变。

### 3. 启动后端 Spring Boot

```bash
cd backend

# 开发模式（直接运行）
mvn spring-boot:run

# 或打包后运行
mvn package -DskipTests
java -jar target/flood-backend-1.0.0.jar
```

启动后：
- REST 接口：`http://localhost:8080/api/**`
- WebSocket 实时推送：`ws://localhost:8080/ws/realtime`
- 默认账号：`admin` / `admin123`

后端会同时：
- 订阅 MQTT 主题 `flood/+/telemetry`，将遥测写入 MySQL；
- 内嵌 CoAP 服务端（Californium）监听 `5683`（可通过 `coap.enabled=false` 关闭）。

### 4. 启动监测站点模拟器

```bash
# 持续循环上报（默认 MQTT，主题 flood/{station_id}/telemetry）
python simulation/run_all.py

# 仅启动前 5 个站点
python simulation/run_all.py --count 5

# 所有站点仅上报一次
python simulation/run_all.py --once
```

切换上报协议：复制 `simulation/.env.example` 为 `simulation/.env`，设置 `PROTOCOL=coap` 即可。

### 5. （可选）启动独立 CoAP 服务端

后端已内嵌 CoAP 服务端，如需独立的 Python CoAP 服务端：

```bash
python coap_server/coap_server.py
```

服务监听 `coap://localhost:5683/telemetry/{station_id}`。

如需将 CoAP 收到的数据转发到 MQTT，设置环境变量：

```bash
# Windows (PowerShell)
$env:FORWARD_TO_MQTT="true"; python coap_server/coap_server.py
# Linux / macOS
FORWARD_TO_MQTT=true python coap_server/coap_server.py
```

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
