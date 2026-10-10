# -*- coding: utf-8 -*-
"""
coap_server.py
==============
基于 aiocoap 的 CoAP 服务端，接收监测站点上报的遥测数据。

资源路径：coap://{host}:{port}/telemetry/{station_id}
请求方法：PUT / POST，Content-Format: application/json

功能：
    - 监听 /telemetry/{station_id}，解析并打印收到的 JSON 数据
    - 返回 2.04 Changed 确认接收
    - 可选：将数据转发到 MQTT（主题 flood/{station_id}/telemetry），
      通过环境变量 FORWARD_TO_MQTT 控制（默认关闭）

运行方式：
    python coap_server/coap_server.py
"""
import asyncio
import json
import logging
import os

from dotenv import load_dotenv

import aiocoap.resource as resource
import aiocoap.numbers.codes as codes
from aiocoap import Context, Message

# 项目根目录（用于加载 simulation/.env 中的公共配置）
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# 加载环境变量：优先当前目录 .env，其次 simulation/.env
load_dotenv()
load_dotenv(os.path.join(BASE_DIR, "simulation", ".env"))

# ---------- 服务监听配置（与 simulation/.env.example 保持一致） ----------
COAP_HOST = os.getenv("COAP_HOST", "localhost")
COAP_PORT = int(os.getenv("COAP_PORT", "5683"))

# 是否将收到的数据转发到 MQTT
FORWARD_TO_MQTT = os.getenv("FORWARD_TO_MQTT", "false").strip().lower() in ("1", "true", "yes")

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger("coap_server")


def _forward_to_mqtt(station_id: str, payload: dict) -> None:
    """将收到的数据转发到 MQTT 主题 flood/{station_id}/telemetry。"""
    try:
        import paho.mqtt.client as mqtt

        topic = f"flood/{station_id}/telemetry"
        broker = os.getenv("MQTT_BROKER", "localhost")
        port = int(os.getenv("MQTT_PORT", "1883"))
        username = os.getenv("MQTT_USERNAME", "Wangwei")
        password = os.getenv("MQTT_PASSWORD", "123456")

        # 兼容 paho-mqtt v1 与 v2 的 Client 构造方式
        try:
            client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
        except (AttributeError, TypeError):
            client = mqtt.Client()

        if username:
            client.username_pw_set(username, password)

        client.connect(broker, port, keepalive=60)
        client.loop_start()
        try:
            client.publish(topic, json.dumps(payload, ensure_ascii=False), qos=1)
        finally:
            client.loop_stop()
            client.disconnect()
        log.info("已转发到 MQTT 主题 %s", topic)
    except Exception as exc:  # 转发失败不影响 CoAP 响应
        log.warning("转发到 MQTT 失败：%s", exc)


class TelemetryResource(resource.Resource):
    """处理 /telemetry/{station_id} 的 CoAP 资源。

    由于站点编号是动态的，这里注册在 /telemetry 前缀下，
    再从 URI 路径的最后一段提取实际的 station_id。
    """

    async def render_put(self, request):
        return await self._handle(request)

    async def render_post(self, request):
        return await self._handle(request)

    async def _handle(self, request):
        # 从 URI 路径中提取站点编号（最后一段，例如 /telemetry/S001 -> S001）
        uri_path = list(getattr(request.opt, "uri_path", None) or [])
        station_id = uri_path[-1] if uri_path else "unknown"

        # 解析 JSON 载荷
        try:
            payload = json.loads(request.payload.decode("utf-8"))
        except (json.JSONDecodeError, UnicodeDecodeError) as exc:
            log.warning("站点 %s 载荷解析失败：%s", station_id, exc)
            return Message(code=codes.BAD_REQUEST, payload=b"invalid JSON")

        log.info("收到站点 %s 遥测数据：%s", station_id, json.dumps(payload, ensure_ascii=False))

        # 可选转发到 MQTT
        if FORWARD_TO_MQTT:
            _forward_to_mqtt(station_id, payload)

        # 返回 2.04 Changed
        return Message(code=codes.CHANGED, payload=b"ok")


async def main() -> None:
    """创建 CoAP 服务端并保持运行。"""
    # 资源树：/telemetry 前缀（station_id 为动态段）
    root = resource.Site()
    root.add_resource(["telemetry"], TelemetryResource())

    bind = (COAP_HOST, COAP_PORT)
    await Context.create_server_context(root, bind=bind)
    log.info("CoAP 服务端已启动，监听 coap://%s:%s/telemetry/{station_id}", COAP_HOST, COAP_PORT)

    # 挂起当前协程，保持服务运行
    await asyncio.Event().wait()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        log.info("CoAP 服务端已停止")
