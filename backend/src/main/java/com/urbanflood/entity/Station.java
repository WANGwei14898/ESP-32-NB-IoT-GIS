package com.urbanflood.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.urbanflood.dto.TelemetryDTO;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 监测站点实体，对应 station 表。
 */
@Data
@TableName("station")
public class Station {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 站点编号（如 S001），全局唯一 */
    private String code;

    /** 站点名称 */
    private String name;

    /** 经度（WGS84） */
    private Double longitude;

    /** 纬度（WGS84） */
    private Double latitude;

    /** 详细地址 */
    private String address;

    /** 所属区域 */
    private String region;

    /** 关联设备 ID */
    private Long deviceId;

    /** 站点状态：ONLINE / OFFLINE / FAULT */
    private String status;

    /** 安装时间 */
    private LocalDateTime installTime;

    /** 当前预警等级（计算字段，不落库） */
    @TableField(exist = false)
    private String alertLevel;

    /** 最新遥测数据（计算字段，不落库） */
    @TableField(exist = false)
    private TelemetryDTO latest;
}
