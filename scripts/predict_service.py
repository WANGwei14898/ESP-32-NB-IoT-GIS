# -*- coding: utf-8 -*-
"""
predict_service.py
==================
短时水位预测服务（独立运行，不阻塞 MQTT / CoAP 主数据接收服务）。

功能：
    1. 数据获取：从 MySQL 读取指定站点过去 2 小时的水位数据
    2. 预处理：重采样 + 线性插值 + 滑动平均平滑去噪
    3. 双模型预测：滑动平均外推 / ARIMA（statsmodels），可通过配置切换
    4. 结果输出：predicted_water_level_30m / 60m + 置信区间，写入 prediction 表
    5. 触发方式：APScheduler 定时任务（每 5 分钟）+ Flask HTTP 接口（/predict）

运行：
    python predict_service.py               # 启动定时任务 + HTTP 服务
    python predict_service.py --once S001   # 手动对单个站点预测一次并打印

配置：复制 .env.example 为 .env，或直接用环境变量覆盖。
"""

import argparse
import json
import logging
import os
import sys
from datetime import datetime, timedelta

import numpy as np
import pandas as pd
import pymysql
from dotenv import load_dotenv

# 加载 scripts/ 同级或当前目录的 .env
load_dotenv()

# ======================= 配置 =======================
DB_HOST = os.getenv("DB_HOST", "localhost")
DB_PORT = int(os.getenv("DB_PORT", "3306"))
DB_USER = os.getenv("DB_USER", "root")
DB_PASSWORD = os.getenv("DB_PASSWORD", "123456")
DB_NAME = os.getenv("DB_NAME", "flood_monitor")

PREDICT_WINDOW_MINUTES = int(os.getenv("PREDICT_WINDOW_MINUTES", "120"))   # 读取过去 2 小时
SMOOTH_WINDOW_MINUTES = int(os.getenv("SMOOTH_WINDOW_MINUTES", "5"))      # 滑动平均窗口（分钟）
PREDICT_MODEL = os.getenv("PREDICT_MODEL", "moving_average").strip().lower()  # moving_average / arima
PREDICT_HORIZON_MINUTES = int(os.getenv("PREDICT_HORIZON_MINUTES", "60"))  # 默认预测 60 分钟
PREDICT_STEP_MINUTES = int(os.getenv("PREDICT_STEP_MINUTES", "5"))         # 预测步长（分钟）
SCHEDULE_INTERVAL_SECONDS = int(os.getenv("PREDICT_SCHEDULE_SECONDS", "300"))  # 定时任务间隔
SERVE_PORT = int(os.getenv("PREDICT_PORT", "8000"))
MIN_POINTS_FOR_ARIMA = int(os.getenv("MIN_POINTS_FOR_ARIMA", "30"))        # ARIMA 所需最少点数

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger("predict_service")


# ======================= 数据获取 =======================
def get_conn():
    """建立 MySQL 连接。"""
    return pymysql.connect(
        host=DB_HOST, port=DB_PORT, user=DB_USER,
        password=DB_PASSWORD, database=DB_NAME, charset="utf8mb4",
    )


def resolve_station_id(station_code):
    """将站点编号（code，如 S001）或数字主键解析为 station.id。"""
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            # 若直接传入数字主键
            if str(station_code).isdigit():
                cur.execute("SELECT id FROM station WHERE id = %s", (int(station_code),))
            else:
                cur.execute("SELECT id FROM station WHERE code = %s", (station_code,))
            row = cur.fetchone()
            if row is None:
                raise ValueError(f"站点 {station_code} 不存在")
            return row[0]
    finally:
        conn.close()


def fetch_history(station_id, minutes=PREDICT_WINDOW_MINUTES):
    """读取指定站点过去 minutes 分钟的水位数据，返回 [(ts, water_level), ...]。"""
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT ts, water_level FROM telemetry "
                "WHERE station_id = %s AND ts >= NOW() - INTERVAL %s MINUTE "
                "ORDER BY ts ASC",
                (station_id, minutes),
            )
            return cur.fetchall()
    finally:
        conn.close()


def list_stations():
    """返回所有站点编号列表（用于定时任务）。"""
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT code FROM station ORDER BY id ASC")
            return [r[0] for r in cur.fetchall()]
    finally:
        conn.close()


# ======================= 数据预处理 =======================
def preprocess(rows, smooth_minutes=SMOOTH_WINDOW_MINUTES):
    """转为 pandas Series：重采样到 1 分钟 -> 线性插值 -> 滑动平均平滑。"""
    if not rows:
        return pd.Series(dtype=float)
    df = pd.DataFrame(rows, columns=["ts", "water_level"])
    df["ts"] = pd.to_datetime(df["ts"])
    df["water_level"] = pd.to_numeric(df["water_level"], errors="coerce")
    df = df.dropna(subset=["water_level"])
    df = df.set_index("ts").sort_index()
    # 同一时间戳去重（保留最后一条）
    df = df[~df.index.duplicated(keep="last")]
    if df.empty:
        return pd.Series(dtype=float)
    # 重采样到 1 分钟并插值
    s = df["water_level"].resample("1min").mean()
    s = s.interpolate(method="linear", limit_direction="both").ffill().bfill()
    # 滑动平均平滑去噪
    return s.rolling(window=smooth_minutes, min_periods=1, center=True).mean()


# ======================= 模型预测 =======================
def forecast_moving_average(series, minutes=PREDICT_HORIZON_MINUTES, step=PREDICT_STEP_MINUTES):
    """滑动平均外推：用最近 N 个点的平均变化率线性外推未来。"""
    values = series.values.astype(float)
    if len(values) < 2:
        last = float(values[-1]) if len(values) else 0.0
        slope = 0.0
    else:
        last = float(values[-1])
        n = min(10, len(values) - 1)
        recent = values[-n - 1:]
        slope = float(np.mean(np.diff(recent))) * step  # 每个预测步长的变化率
    # 残差标准差（用于置信区间）
    resid_std = float(np.std(np.diff(values))) if len(values) >= 3 else max(abs(last) * 0.05, 0.01)

    last_time = series.index[-1]
    points = []
    for i in range(1, minutes // step + 1):
        t = last_time + timedelta(minutes=step * i)
        val = max(0.0, last + slope * i)
        margin = float(1.96 * resid_std * np.sqrt(i))
        points.append({
            "time": t.strftime("%Y-%m-%dT%H:%M:%S"),
            "value": round(val, 3),
            "lower": round(max(0.0, val - margin), 3),
            "upper": round(val + margin, 3),
        })
    return points


def forecast_arima(series, minutes=PREDICT_HORIZON_MINUTES, step=PREDICT_STEP_MINUTES):
    """ARIMA 模型（statsmodels）：自动差分 + 95% 置信区间。"""
    from statsmodels.tsa.arima.model import ARIMA

    steps = minutes // step
    order = (1, 1, 1) if len(series) >= MIN_POINTS_FOR_ARIMA else (1, 0, 0)
    fitted = ARIMA(series, order=order).fit()
    forecast = fitted.get_forecast(steps=steps)
    mean = forecast.predicted_mean
    conf = forecast.conf_int(alpha=0.05)

    last_time = series.index[-1]
    points = []
    for i in range(steps):
        t = last_time + timedelta(minutes=step * (i + 1))
        val = float(mean.iloc[i])
        lower = float(conf.iloc[i, 0])
        upper = float(conf.iloc[i, 1])
        points.append({
            "time": t.strftime("%Y-%m-%dT%H:%M:%S"),
            "value": round(max(0.0, val), 3),
            "lower": round(max(0.0, lower), 3),
            "upper": round(upper, 3),
        })
    return points


# ======================= 预测主流程 =======================
def predict(station_code, minutes=PREDICT_HORIZON_MINUTES, model=None):
    """对指定站点做预测，返回结果字典；数据不足或 ARIMA 失败自动降级。"""
    station_id = resolve_station_id(station_code)
    rows = fetch_history(station_id, PREDICT_WINDOW_MINUTES)
    series = preprocess(rows)

    if series.empty or len(series) < 2:
        raise ValueError(f"站点 {station_code} 历史数据不足（{len(series)} 点），无法预测")

    model_name = (model or PREDICT_MODEL).strip().lower()

    try:
        if model_name == "arima" and len(series) >= MIN_POINTS_FOR_ARIMA:
            points = forecast_arima(series, minutes)
            used_model = "arima"
        else:
            points = forecast_moving_average(series, minutes)
            used_model = "moving_average"
    except Exception as exc:
        # 模型失败自动降级为滑动平均，不崩溃
        log.warning("模型 %s 预测失败，降级为移动平均：%s", model_name, exc)
        points = forecast_moving_average(series, minutes)
        used_model = "moving_average"

    step = PREDICT_STEP_MINUTES
    idx30 = 30 // step - 1
    idx60 = 60 // step - 1

    def pick(idx):
        return points[idx] if 0 <= idx < len(points) else points[-1]

    p30, p60 = pick(idx30), pick(idx60)

    result = {
        "station_id": str(station_code),
        "prediction_time": datetime.now().strftime("%Y-%m-%dT%H:%M:%S"),
        "model": used_model,
        "predicted_water_level_30m": p30["value"],
        "predicted_water_level_60m": p60["value"],
        "confidence_interval": {
            "lower_30m": p30["lower"],
            "upper_30m": p30["upper"],
            "lower_60m": p60["lower"],
            "upper_60m": p60["upper"],
        },
        "points": points,
    }
    save_prediction(station_id, used_model, points)
    return result


# ======================= 结果落库 =======================
def save_prediction(station_id, model, points):
    """将预测结果写入 prediction 表（points 为 JSON，兼容后端 PredictionPoint）。"""
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute(
                "INSERT INTO prediction (station_id, model_type, predict_time, points) "
                "VALUES (%s, %s, NOW(), %s)",
                (station_id, model, json.dumps(points, ensure_ascii=False)),
            )
        conn.commit()
    finally:
        conn.close()


# ======================= HTTP 服务（供后端实时调用） =======================
try:
    from flask import Flask, request, jsonify

    _app = Flask(__name__)

    @_app.route("/predict", methods=["POST"])
    def predict_api():
        data = request.get_json(silent=True) or {}
        station_code = data.get("station_id") or data.get("stationId") or data.get("station_code")
        if not station_code:
            return jsonify({"error": "station_id 不能为空"}), 400
        minutes = int(data.get("minutes", PREDICT_HORIZON_MINUTES))
        model = data.get("model")
        try:
            return jsonify(predict(station_code, minutes, model))
        except Exception as exc:
            log.error("预测失败：%s", exc)
            return jsonify({"error": str(exc)}), 500

    @_app.route("/health", methods=["GET"])
    def health():
        return jsonify({"status": "ok"})

except ImportError:
    _app = None
    log.warning("未安装 Flask，跳过 HTTP 服务（仅定时任务/单次预测可用）")


# ======================= 定时任务 =======================
def scheduled_predict():
    """定时为所有站点执行预测并落库。"""
    try:
        stations = list_stations()
        if not stations:
            log.warning("无站点，跳过定时预测")
            return
        for code in stations:
            try:
                predict(code)
                log.info("定时预测完成：站点 %s", code)
            except Exception as exc:
                log.warning("站点 %s 定时预测失败：%s", code, exc)
    except Exception as exc:
        log.error("定时预测任务异常：%s", exc)


# ======================= 入口 =======================
def main():
    parser = argparse.ArgumentParser(description="短时水位预测服务")
    parser.add_argument("--once", metavar="STATION", help="手动对单个站点预测一次后退出")
    parser.add_argument("--no-http", action="store_true", help="不启动 HTTP 服务（仅定时任务）")
    args = parser.parse_args()

    if args.once:
        result = predict(args.once)
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return

    scheduler = None
    try:
        from apscheduler.schedulers.background import BackgroundScheduler

        scheduler = BackgroundScheduler()
        scheduler.add_job(scheduled_predict, "interval", seconds=SCHEDULE_INTERVAL_SECONDS, id="predict")
        scheduler.start()
        log.info("APScheduler 已启动，每 %s 秒预测一次", SCHEDULE_INTERVAL_SECONDS)
        scheduled_predict()  # 启动即执行一次
    except ImportError:
        log.warning("未安装 APScheduler，跳过定时任务")

    if args.no_http or _app is None:
        log.info("预测服务以纯定时任务模式运行")
        try:
            import time

            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            log.info("预测服务已停止")
    else:
        log.info("预测 HTTP 服务启动于端口 %s", SERVE_PORT)
        _app.run(host="0.0.0.0", port=SERVE_PORT, use_reloader=False)


if __name__ == "__main__":
    main()

