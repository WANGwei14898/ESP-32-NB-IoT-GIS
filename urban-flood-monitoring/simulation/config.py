# -*- coding: utf-8 -*-
"""
config.py
=========
集中管理城市内涝监测模拟器的配置信息，包括：
    - MQTT / CoAP 服务端地址
    - 监测站点列表（站点编号、名称、经纬度）
    - 上报间隔（1~5 分钟可配置）
    - 各类监测指标的阈值

配置优先级：.env 环境变量 > 本文件中的默认值。
"""
import os

from dotenv import load_dotenv

# 加载 simulation/ 目录下的 .env 文件（若存在）
load_dotenv()


def _get_int(key: str, default: int) -> int:
    """从环境变量读取整型配置，失败时返回默认值。"""
    try:
        return int(os.getenv(key, default))
    except (TypeError, ValueError):
        return default


def _get_float(key: str, default: float) -> float:
    """从环境变量读取浮点型配置，失败时返回默认值。"""
    try:
        return float(os.getenv(key, default))
    except (TypeError, ValueError):
        return default


# ======================= MQTT 配置 =======================
MQTT_BROKER_HOST = os.getenv("MQTT_BROKER_HOST", "localhost")
MQTT_BROKER_PORT = _get_int("MQTT_BROKER_PORT", 1883)
MQTT_USERNAME = os.getenv("MQTT_USERNAME", "Wangwei")
MQTT_PASSWORD = os.getenv("MQTT_PASSWORD", "123456")

# ======================= CoAP 配置 =======================
COAP_HOST = os.getenv("COAP_HOST", "localhost")
COAP_PORT = _get_int("COAP_PORT", 5683)

# ======================= 上报协议 =======================
# 取值：mqtt / coap
PROTOCOL = os.getenv("PROTOCOL", "mqtt").strip().lower()
if PROTOCOL not in ("mqtt", "coap"):
    PROTOCOL = "mqtt"

# ======================= 上报间隔（秒） =======================
# 每个站点每次上报间隔在 [REPORT_INTERVAL_MIN, REPORT_INTERVAL_MAX] 内随机取值。
# 默认 60 ~ 300 秒，即 1 ~ 5 分钟，可通过 .env 覆盖。
REPORT_INTERVAL_MIN = _get_int("REPORT_INTERVAL_MIN", 60)
REPORT_INTERVAL_MAX = _get_int("REPORT_INTERVAL_MAX", 300)

# ======================= 站点列表 =======================
# 站点编号全局唯一，经纬度使用广州市区的近似坐标（WGS84）。
# 每个站点包含：station_id（编号）、name（名称）、latitude（纬度）、longitude（经度）。
STATIONS = [
    {"station_id": "S001", "name": "天河区-棠下涌", "latitude": 23.1291, "longitude": 113.3570},
    {"station_id": "S002", "name": "越秀区-东濠涌", "latitude": 23.1290, "longitude": 113.2644},
    {"station_id": "S003", "name": "海珠区-康乐涌", "latitude": 23.0950, "longitude": 113.2880},
    {"station_id": "S004", "name": "荔湾区-荔枝湾涌", "latitude": 23.1170, "longitude": 113.2380},
    {"station_id": "S005", "name": "白云区-石井河", "latitude": 23.2100, "longitude": 113.2300},
    {"station_id": "S006", "name": "黄埔区-南岗河", "latitude": 23.0930, "longitude": 113.5400},
    {"station_id": "S007", "name": "番禺区-市桥水道", "latitude": 22.9370, "longitude": 113.3620},
    {"station_id": "S008", "name": "南沙区-蕉门河", "latitude": 22.8010, "longitude": 113.5250},
    {"station_id": "S009", "name": "增城区-增江", "latitude": 23.2900, "longitude": 113.8300},
    {"station_id": "S010", "name": "花都区-天马河", "latitude": 23.4040, "longitude": 113.1920},
]

# ======================= 阈值配置 =======================
# 供后端 / 前端做预警判断使用，模拟器本身不强制告警。
THRESHOLDS = {
    "water_level_warning": 1.0,   # 水位预警阈值（米）
    "water_level_danger": 1.5,    # 水位危险阈值（米）
    "rainfall_heavy": 50.0,       # 大雨阈值（mm/h）
    "flow_speed_high": 3.0,       # 高流速阈值（m/s）
    "battery_low": 20.0,          # 低电量阈值（%）
    "signal_weak": -100,          # 弱信号阈值（dBm）
}


def get_station(station_id: str):
    """根据站点编号返回站点字典，找不到时返回 None。"""
    for station in STATIONS:
        if station["station_id"] == station_id:
            return station
    return None

