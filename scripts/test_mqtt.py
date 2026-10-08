# -*- coding: utf-8 -*-
"""
test_mqtt.py
============
MQTT 连通性测试脚本。

功能：
    - 默认订阅 flood/+/telemetry 主题，打印收到的遥测消息（用于验证模拟器上报）
    - 使用 --publish 参数时，向指定主题发布一条测试消息（用于验证 Broker）

用法：
    python scripts/test_mqtt.py                 # 订阅并打印所有站点遥测数据
    python scripts/test_mqtt.py --publish       # 发布一条测试消息
    python scripts/test_mqtt.py --topic flood/S001/telemetry --publish
"""
import argparse
import json
import time

import paho.mqtt.client as mqtt


def on_connect(client, userdata, flags, reason_code, properties=None):
    """连接成功回调（兼容 paho-mqtt v1 与 v2 的回调签名）。"""
    print(f"已连接 MQTT Broker（rc={reason_code}）")
    if not userdata.get("publish"):
        topic = userdata["topic"]
        client.subscribe(topic)
        print(f"已订阅主题：{topic}")


def on_message(client, userdata, msg):
    """收到消息回调：尝试按 JSON 解析并打印。"""
    try:
        payload = json.loads(msg.payload.decode("utf-8"))
        print(f"[{msg.topic}] {json.dumps(payload, ensure_ascii=False)}")
    except (json.JSONDecodeError, UnicodeDecodeError):
        print(f"[{msg.topic}] {msg.payload.decode('utf-8', errors='replace')}")


def main() -> None:
    parser = argparse.ArgumentParser(description="MQTT 连通性测试")
    parser.add_argument("--host", default="localhost", help="Broker 地址")
    parser.add_argument("--port", type=int, default=1883, help="Broker 端口")
    parser.add_argument("--topic", default="flood/+/telemetry", help="主题")
    parser.add_argument("--publish", action="store_true", help="发布测试消息")
    args = parser.parse_args()

    # 兼容 paho-mqtt v1 与 v2 的 Client 构造方式
    try:
        client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
    except (AttributeError, TypeError):
        client = mqtt.Client()

    client.user_data_set({"topic": args.topic, "publish": args.publish})
    client.on_connect = on_connect
    client.on_message = on_message

    client.connect(args.host, args.port, keepalive=60)

    if args.publish:
        # 发布一条测试消息，验证 Broker 与主题
        test_payload = {
            "station_id": "TEST001",
            "timestamp": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
            "water_level": 0.55,
            "rainfall": 5.2,
            "flow_speed": 0.9,
            "battery": 99.0,
            "signal": -80.0,
            "latitude": 23.1291,
            "longitude": 113.3570,
        }
        client.loop_start()
        info = client.publish(args.topic, json.dumps(test_payload, ensure_ascii=False), qos=1)
        info.wait_for_publish()
        print(f"已发布测试消息到主题 {args.topic}")
        client.loop_stop()
        client.disconnect()
        return

    try:
        client.loop_forever()
    except KeyboardInterrupt:
        print("\n测试结束")
        client.disconnect()


if __name__ == "__main__":
    main()
