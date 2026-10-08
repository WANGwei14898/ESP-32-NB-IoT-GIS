package com.urbanflood.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警实体，对应 alert 表。
 */
@Data
@TableName("alert")
public class Alert {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 站点 ID */
    private Long stationId;

    /** 站点名称（关联字段，不落库） */
    @TableField(exist = false)
    private String stationName;

    /** 预警等级：BLUE / YELLOW / ORANGE / RED */
    private String level;

    /** 告警类型：WATER / RAIN / FLOW / BATTERY / SIGNAL */
    private String type;

    /** 告警描述 */
    private String message;

    /** 触发水位（米） */
    private Double waterLevel;

    /** 触发阈值 */
    private Double threshold;

    /** 处理状态：ACTIVE / HANDLED */
    private String status;

    /** 处理人，列名 handler_name（handler 为 MySQL 保留字） */
    @TableField("handler_name")
    private String handler;

    /** 处理备注 */
    private String remark;

    /** 处理时间 */
    private LocalDateTime handledTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
