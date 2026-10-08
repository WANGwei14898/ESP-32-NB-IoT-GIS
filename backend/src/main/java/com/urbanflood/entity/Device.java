package com.urbanflood.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 监测设备实体，对应 device 表。
 */
@Data
@TableName("device")
public class Device {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备编号 */
    private String code;

    /** 设备名称 */
    private String name;

    /** 关联站点 ID */
    private Long stationId;

    /** 站点名称（关联字段，不落库） */
    @TableField(exist = false)
    private String stationName;

    /** 设备类型：NB-IoT / LoRa / 4G 等 */
    private String type;

    /** 上报协议：MQTT / CoAP */
    private String protocol;

    /** 设备状态：ONLINE / OFFLINE / FAULT */
    private String status;

    /** 最近心跳时间 */
    private LocalDateTime lastHeartbeat;

    /** 电量（%） */
    private Double battery;

    /** 信号强度（dBm），列名 signal_strength */
    @TableField("signal_strength")
    private Double signal;

    /** 固件版本 */
    private String firmware;

    /** 安装时间 */
    private LocalDateTime installTime;
}
