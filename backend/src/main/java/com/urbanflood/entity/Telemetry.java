package com.urbanflood.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 遥测数据实体，对应 telemetry 表（按月分区）。
 */
@Data
@TableName("telemetry")
public class Telemetry {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 站点 ID */
    private Long stationId;

    /** 设备 ID */
    private Long deviceId;

    /** 水位（米） */
    private Double waterLevel;

    /** 雨量（mm/h） */
    private Double rainfall;

    /** 流速（m/s） */
    private Double flowVelocity;

    /** 电量（%） */
    private Double battery;

    /** 信号强度（dBm），列名 signal_strength（signal 为 MySQL 保留字，字段改名避免别名冲突） */
    @TableField("signal_strength")
    private Double signalStrength;

    /** 采集时间 */
    private LocalDateTime ts;
}
