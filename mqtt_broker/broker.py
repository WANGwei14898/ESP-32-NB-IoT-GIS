# -*- coding: utf-8 -*-
"""
broker.py
=========
纯 Python 实现的轻量 MQTT Broker（基于 amqtt），用于替代 Docker 中的 EMQX。

无需 Docker、无需额外安装消息中间件，只需：
    pip install -r requirements.txt
    python mqtt_broker/broker.py

监听配置（可通过环境变量覆盖）：
    MQTT_HOST   默认 0.0.0.0
    MQTT_PORT   默认 1883

默认开启匿名访问，任何客户端无需账号即可连接/订阅/发布。
"""

import asyncio
import logging
import os

from amqtt.broker import Broker

# ---------- 监听配置 ----------
MQTT_HOST = os.getenv("MQTT_HOST", "0.0.0.0")
MQTT_PORT = int(os.getenv("MQTT_PORT", "1883"))

config = {
    "listeners": {
        "default": {
            "type": "tcp",
            "bind": f"{MQTT_HOST}:{MQTT_PORT}",
            "max-connections": 50000,
        },
    },
    "sys_interval": 10,
    "auth": {
        "allow-anonymous": True,
    },
    "topic-check": {
        "enabled": False,
    },
}

logging.basicConfig(level=logging.INFO,
                    format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger("mqtt_broker")


async def main() -> None:
    broker = Broker(config)
    await broker.start()
    log.info("MQTT Broker 已启动，监听 %s:%s", MQTT_HOST, MQTT_PORT)
    log.info("模拟器可通过主题 flood/{station_id}/telemetry 上报数据")
    # 挂起当前协程，保持运行
    await asyncio.Event().wait()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        log.info("MQTT Broker 已停止")
