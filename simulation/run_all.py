# -*- coding: utf-8 -*-
"""
run_all.py
==========
一键启动所有监测站点的模拟上报。

用法示例：
    python run_all.py               # 启动全部站点，持续循环上报
    python run_all.py --count 5     # 仅启动前 5 个站点
    python run_all.py --once        # 所有站点仅上报一次后退出
"""
import argparse
import json
import threading

import config
from station_simulator import StationSimulator


def main() -> None:
    parser = argparse.ArgumentParser(description="城市内涝多站点监测模拟器")
    parser.add_argument(
        "--count",
        type=int,
        default=None,
        help="启动的站点数量（默认启动全部站点）",
    )
    parser.add_argument(
        "--once",
        action="store_true",
        help="每个站点仅手动触发一次上报后退出",
    )
    args = parser.parse_args()

    # 确定本次要启动的站点数量：
    # 优先级：命令行 --count > .env 中的 STATION_COUNT > 全部站点
    if args.count is not None:
        count = args.count
    elif config.STATION_COUNT > 0:
        count = config.STATION_COUNT
    else:
        count = len(config.STATIONS)

    stations = config.STATIONS[:count]

    print(f"上报协议：{config.PROTOCOL}")
    print(f"将启动 {len(stations)} 个监测站点："
          f"{', '.join(s['station_id'] for s in stations)}")

    simulators = [StationSimulator(station) for station in stations]

    if args.once:
        # 所有站点依次手动上报一次
        for sim in simulators:
            payload = sim.report_once()
            print(json.dumps(payload, ensure_ascii=False))
        print("已完成一次上报")
        return

    # 每个站点一个线程，并发运行
    threads = []
    for sim in simulators:
        thread = threading.Thread(target=sim.run, name=sim.station_id, daemon=True)
        thread.start()
        threads.append(thread)

    try:
        for thread in threads:
            thread.join()
    except KeyboardInterrupt:
        for sim in simulators:
            sim.stop()
        print("\n已停止所有监测站点模拟器")


if __name__ == "__main__":
    main()
