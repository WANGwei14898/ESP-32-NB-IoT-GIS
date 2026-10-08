# -*- coding: utf-8 -*-
"""
test_coap.py
============
CoAP 连通性测试脚本：向 CoAP 服务端发送一条 PUT 遥测数据并打印响应。

用法：
    python scripts/test_coap.py                    # 默认站点编号 TEST001
    python scripts/test_coap.py --station S001
    python scripts/test_coap.py --host localhost --port 5683
"""
import argparse
import asyncio
import json
import time

from aiocoap import Context, Message


async def send_telemetry(host: str, port: int, station_id: str) -> None:
    """向 coap://{host}:{port}/telemetry/{station_id} 发送一条 PUT 遥测数据。"""
    uri = f"coap://{host}:{port}/telemetry/{station_id}"
    payload = {
        "station_id": station_id,
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "water_level": 0.55,
        "rainfall": 5.2,
        "flow_speed": 0.9,
        "battery": 99.0,
        "signal": -80.0,
        "latitude": 23.1291,
        "longitude": 113.3570,
    }

    request = Message(
        code=Message.PUT,
        uri=uri,
        payload=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
    )
    request.opt.content_format = 50  # Content-Format: application/json

    context = await Context.create_client_context()
    try:
        response = await context.request(request).response
        print(f"响应代码：{response.code}")
        print(f"响应内容：{response.payload.decode('utf-8', errors='replace')}")
    finally:
        await context.shutdown()


def main() -> None:
    parser = argparse.ArgumentParser(description="CoAP 连通性测试")
    parser.add_argument("--host", default="localhost", help="CoAP 服务端地址")
    parser.add_argument("--port", type=int, default=5683, help="CoAP 服务端端口")
    parser.add_argument("--station", default="TEST001", help="站点编号")
    args = parser.parse_args()

    asyncio.run(send_telemetry(args.host, args.port, args.station))


if __name__ == "__main__":
    main()
