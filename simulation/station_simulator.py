# -*- coding: utf-8 -*-
"""
station_simulator.py
====================
单站点模拟器：为单个监测站点生成数据并按配置的协议上报。

功能：
    - 支持 MQTT 上报，主题：flood/{station_id}/telemetry
    - 支持 CoAP 上报，资源：coap://{host}:{port}/telemetry/{station_id}
    - 支持定时循环上报，间隔在 [REPORT_INTERVAL_MIN, REPORT_INTERVAL_MAX] 内随机取值
    - 支持手动触发单次上报（report_once 方法 / --once 命令行参数）

上报 JSON 格式：
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
"""
import json
import os
import random
import sys
import threading
import time
from datetime import datetime

# 确保可以从任意工作目录运行脚本（导入同目录下的 config 与 data_generator）
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import config  # noqa: E402
from data_generator import DataGenerator  # noqa: E402


class StationSimulator:
    """单个监测站点的模拟器。"""

    def __init__(self, station: dict):
        self.station = station
        self.station_id = station["station_id"]
        # 使用站点编号生成稳定的随机种子，保证数据可复现
        self.generator = DataGenerator(self.station_id, seed=hash(self.station_id) % (2 ** 32))
        self.protocol = config.PROTOCOL
        # 停止事件，用于优雅地结束循环上报
        self._stop_event = threading.Event()

    # ------------------------------------------------------------------
    # 数据组装
    # ------------------------------------------------------------------
    def _build_payload(self) -> dict:
        """生成一帧监测数据，并组装为上报 JSON 字典。"""
        data = self.generator.generate()
        # 使用本地时区、秒级精度的 ISO 8601 时间戳
        timestamp = datetime.now().astimezone().isoformat(timespec="seconds")
        return {
            "station_id": self.station_id,
            "timestamp": timestamp,
            "water_level": data["water_level"],
            "rainfall": data["rainfall"],
            "flow_speed": data["flow_speed"],
            "battery": data["battery"],
            "signal": data["signal"],
            "latitude": self.station["latitude"],
            "longitude": self.station["longitude"],
        }

    # ------------------------------------------------------------------
    # 手动触发单次上报
    # ------------------------------------------------------------------
    def report_once(self) -> dict:
        """手动触发一次上报，并返回本次上报的 payload 字典。"""
        payload = self._build_payload()
        if self.protocol == "mqtt":
            self._publish_mqtt(payload)
        else:
            self._publish_coap(payload)
        return payload

    # ------------------------------------------------------------------
    # 定时循环上报
    # ------------------------------------------------------------------
    def run(self) -> None:
        """持续循环上报，直到调用 stop() 为止。"""
        while not self._stop_event.is_set():
            try:
                payload = self.report_once()
                print(f"[{payload['timestamp']}] 站点 {self.station_id} 已上报 "
                      f"水位={payload['water_level']}m 雨量={payload['rainfall']}mm/h")
            except Exception as exc:  # 单次上报失败不应中断整个循环
                print(f"[警告] 站点 {self.station_id} 上报失败：{exc}")
            # 在区间内随机取上报间隔（秒）
            interval = random.uniform(config.REPORT_INTERVAL_MIN, config.REPORT_INTERVAL_MAX)
            self._stop_event.wait(interval)

    def stop(self) -> None:
        """停止循环上报。"""
        self._stop_event.set()


    # ------------------------------------------------------------------
    # MQTT 上报
    # ------------------------------------------------------------------
    def _publish_mqtt(self, payload: dict) -> None:
        """通过 MQTT 将数据发布到主题 flood/{station_id}/telemetry。"""
        import paho.mqtt.client as mqtt

        topic = f"flood/{self.station_id}/telemetry"
        # 兼容 paho-mqtt v1 与 v2 的 Client 构造方式
        try:
            client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
        except (AttributeError, TypeError):
            client = mqtt.Client()

        if config.MQTT_USERNAME:
            client.username_pw_set(config.MQTT_USERNAME, config.MQTT_PASSWORD)

        client.connect(config.MQTT_BROKER, config.MQTT_PORT, keepalive=60)
        client.loop_start()
        try:
            client.publish(topic, json.dumps(payload, ensure_ascii=False), qos=1)
            time.sleep(0.3)  # 短暂等待以确保消息发出
        finally:
            client.loop_stop()
            client.disconnect()

    # ------------------------------------------------------------------
    # CoAP 上报
    # ------------------------------------------------------------------
    def _publish_coap(self, payload: dict) -> None:
        """通过 CoAP PUT 将数据发送到 coap://{host}:{port}/telemetry/{station_id}。"""
        import asyncio

        try:
            # 正常情况：当前线程没有事件循环
            asyncio.run(self._coap_put(payload))
        except RuntimeError:
            # 已在事件循环内运行的情况：新建并运行一个临时事件循环
            loop = asyncio.new_event_loop()
            try:
                loop.run_until_complete(self._coap_put(payload))
            finally:
                loop.close()

    async def _coap_put(self, payload: dict) -> None:
        """异步发送 CoAP PUT 请求。"""
        from aiocoap import Context, Message

        uri = f"coap://{config.COAP_HOST}:{config.COAP_PORT}/telemetry/{self.station_id}"
        request = Message(
            code=Message.PUT,
            uri=uri,
            payload=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        )
        request.opt.content_format = 50  # Content-Format: application/json

        protocol = await Context.create_client_context()
        try:
            await protocol.request(request).response
        finally:
            await protocol.shutdown()


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="城市内涝单站点监测模拟器")
    parser.add_argument(
        "--station",
        default=config.STATIONS[0]["station_id"],
        help="站点编号（默认使用第一个站点）",
    )
    parser.add_argument(
        "--once",
        action="store_true",
        help="仅手动触发一次上报后退出",
    )
    args = parser.parse_args()

    station = config.get_station(args.station) or config.STATIONS[0]
    simulator = StationSimulator(station)

    if args.once:
        # 手动触发一次上报并打印结果
        result = simulator.report_once()
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        try:
            simulator.run()
        except KeyboardInterrupt:
            simulator.stop()
            print(f"\n站点 {simulator.station_id} 模拟器已停止")
