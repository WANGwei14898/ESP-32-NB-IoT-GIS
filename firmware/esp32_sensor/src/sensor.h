/**
 * sensor.h
 * ========
 * HC-SR04 超声波测距驱动 + 滤波（中值/卡尔曼）+ 水位换算。
 */
#pragma once

#include <Arduino.h>

/**
 * 中值滤波器：维护一个滑动窗口，输出窗口内中位数，
 * 用于去除超声波传感器偶发的尖峰噪点。
 */
class MedianFilter {
public:
    explicit MedianFilter(size_t windowSize)
        : _size(windowSize), _buf(new float[windowSize]), _count(0) {}

    ~MedianFilter() { delete[] _buf; }

    // 送入一个采样值，返回滤波后的中位数
    float update(float value) {
        _buf[_count % _size] = value;
        _count++;
        size_t n = (_count < _size) ? _count : _size;
        // 简单插入排序求中位数（窗口很小，性能可接受）
        for (size_t i = 1; i < n; i++) {
            float key = _buf[i];
            size_t j = i;
            while (j > 0 && _buf[j - 1] > key) {
                _buf[j] = _buf[j - 1];
                j--;
            }
            _buf[j] = key;
        }
        return _buf[n / 2];
    }

private:
    size_t _size;
    float* _buf;
    size_t _count;
};

/**
 * 一维卡尔曼滤波器：对超声波距离做平滑，抑制随机噪声。
 */
class KalmanFilter {
public:
    KalmanFilter(float q, float r)
        : _q(q), _r(r), _x(0), _p(1) {}

    float update(float measurement) {
        // 预测
        _p += _q;
        // 更新
        float k = _p / (_p + _r);
        _x += k * (measurement - _x);
        _p *= (1 - k);
        return _x;
    }

private:
    float _q, _r;   // 过程噪声 / 测量噪声
    float _x;       // 状态估计
    float _p;       // 估计误差协方差
};

/**
 * HC-SR04 超声波传感器封装。
 * 负责引脚初始化、单次测距（带超时保护）与量程校验。
 */
class UltrasonicSensor {
public:
    UltrasonicSensor(int trigPin, int echoPin, float maxRangeM)
        : _trig(trigPin), _echo(echoPin), _maxRange(maxRangeM) {}

    void begin() {
        pinMode(_trig, OUTPUT);
        pinMode(_echo, INPUT);
        digitalWrite(_trig, LOW);
    }

    /**
     * 单次测距，返回距离（米）。
     * 读取失败或超出量程返回 -1。
     */
    float readDistance() {
        digitalWrite(_trig, LOW);
        delayMicroseconds(2);
        digitalWrite(_trig, HIGH);
        delayMicroseconds(10);
        digitalWrite(_trig, LOW);

        // 回响脉冲时长（微秒），带超时保护（对应最大量程）
        unsigned long timeout = (unsigned long)((_maxRange * 2.0f / SOUND_SPEED_MPS) * 1e6) + 1000;
        unsigned long duration = pulseIn(_echo, HIGH, timeout);
        if (duration == 0) {
            return -1.0f;  // 超时，无回波
        }
        float distance = (duration / 1e6f) * SOUND_SPEED_MPS / 2.0f;
        if (distance > _maxRange || distance <= 0.0f) {
            return -1.0f;  // 超出量程
        }
        return distance;
    }

private:
    int _trig, _echo;
    float _maxRange;
};

/**
 * 水位监测器：组合传感器、滤波与「水位 = 安装高度 - 实测距离」换算。
 */
class WaterLevelMonitor {
public:
    WaterLevelMonitor(UltrasonicSensor& sensor, float sensorHeightM)
        : _sensor(sensor), _sensorHeight(sensorHeightM)
#if ENABLE_KALMAN
        , _kalman(KALMAN_Q, KALMAN_R)
#endif
        , _median(MEDIAN_WINDOW_SIZE) {}

    /**
     * 采集一帧水位（米）。
     * 内部执行多次测距 + 滤波；全部失败返回 NAN。
     */
    float readWaterLevel() {
        float best = NAN;
        int success = 0;
        for (int i = 0; i < SENSOR_RETRY_TIMES; i++) {
            float d = _sensor.readDistance();
            if (d > 0.0f) {
#if ENABLE_KALMAN
                float filtered = _kalman.update(d);
#else
                float filtered = _median.update(d);
#endif
                _lastDistance = filtered;
                float level = _sensorHeight - filtered;
                // 水位不应为负（距离大于安装高度视为贴底/异常）
                best = (level < 0.0f) ? 0.0f : level;
                success++;
            }
            delay(20);
        }
        return success > 0 ? best : NAN;
    }

    /** 最近一次滤波后的超声波距离（米），用于上报 distance 字段。 */
    float lastDistance() const { return _lastDistance; }

    /** 当前传感器安装高度（米），可运行时更新。 */
    void setSensorHeight(float h) { _sensorHeight = h; }

private:
    UltrasonicSensor& _sensor;
    float _sensorHeight;
    float _lastDistance = 0.0f;
#if ENABLE_KALMAN
    KalmanFilter _kalman;
#endif
    MedianFilter _median;
};
