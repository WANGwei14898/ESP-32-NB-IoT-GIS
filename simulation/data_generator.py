# -*- coding: utf-8 -*-
"""
data_generator.py
=================
数据生成器：模拟产生监测点上报的水位、雨量、流速、电量、信号强度等数据。

设计要点：
    - 每个站点拥有独立的随机数生成器（通过 seed 固定），保证结果可复现；
    - 数据带有「随机波动」与「缓慢趋势」：水位随降雨缓慢抬升，
      流速与水位正相关，电量缓慢下降，信号围绕均值波动；
    - 偶尔出现暴雨峰值，用于模拟内涝诱因。
"""
import random


class DataGenerator:
    """单个监测站点的数据生成器，维护内部状态以产生连续、合理的数据序列。"""

    def __init__(self, station_id: str, seed: int = None):
        # 站点编号，用于日志与可复现的随机种子
        self.station_id = station_id
        # 独立随机数生成器，seed 固定后每次运行产生相同的数据序列
        self.rng = random.Random(seed if seed is not None else hash(station_id) % (2 ** 32))

        # 当前监测值（初始状态）
        self.water_level = self.rng.uniform(0.2, 0.6)   # 水位（米）
        self.rainfall = self.rng.uniform(0.0, 10.0)     # 雨量（mm/h）
        self.flow_speed = self.rng.uniform(0.3, 1.5)    # 流速（m/s）
        self.battery = 100.0                             # 电量（%）
        self.signal_mean = self.rng.uniform(-90, -70)    # 信号强度均值（dBm）
        self.signal = self.signal_mean

        # 缓慢趋势参数
        self.trend_step = self.rng.uniform(0.001, 0.005)  # 每步趋势增量
        self.step = 0                                     # 已生成的步数

    def generate(self) -> dict:
        """生成一帧监测数据，返回包含各项指标的字典。"""
        self.step += 1

        # ---------- 雨量：基础值 + 缓慢趋势 + 随机波动，偶发暴雨 ----------
        base_rain = max(0.0, self.rainfall + self.trend_step * 0.5)
        self.rainfall = max(0.0, base_rain + self.rng.gauss(0, 1.5))
        if self.rng.random() < 0.03:  # 3% 概率出现暴雨峰值，模拟内涝诱因
            self.rainfall += self.rng.uniform(20.0, 60.0)

        # ---------- 水位：随降雨缓慢上升，带随机噪声，并缓慢回落 ----------
        self.water_level += 0.02 * (self.rainfall / 50.0) + self.rng.gauss(0, 0.005)
        self.water_level -= self.trend_step * 0.05  # 无降雨时的自然回落趋势
        self.water_level = max(0.0, self.water_level)

        # ---------- 流速：与水位正相关，叠加随机扰动 ----------
        self.flow_speed = max(
            0.0,
            0.5 + 1.5 * (self.water_level / 2.0) + self.rng.gauss(0, 0.05),
        )

        # ---------- 电量：缓慢下降，低于 10% 时模拟更换电池 ----------
        self.battery -= self.rng.uniform(0.001, 0.01)
        if self.battery < 10.0:
            self.battery = 100.0
        self.battery = round(max(0.0, min(100.0, self.battery)), 2)

        # ---------- 信号强度：围绕均值随机波动，限制在合理范围 ----------
        self.signal = self.signal_mean + self.rng.gauss(0, 3.0)
        self.signal = round(max(-140.0, min(-40.0, self.signal)), 2)

        return {
            "water_level": round(self.water_level, 3),
            "rainfall": round(self.rainfall, 2),
            "flow_speed": round(self.flow_speed, 3),
            "battery": self.battery,
            "signal": self.signal,
        }


if __name__ == "__main__":
    # 简单自测：连续生成 10 帧数据并打印
    gen = DataGenerator("S001", seed=42)
    for _ in range(10):
        print(gen.generate())
