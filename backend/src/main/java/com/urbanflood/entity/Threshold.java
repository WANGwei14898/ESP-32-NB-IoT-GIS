package com.urbanflood.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 告警阈值配置实体，对应 threshold 表。
 */
@Data
@TableName("threshold")
public class Threshold {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 阈值编码：WATER_* / RAIN_* / FLOW_*（各蓝黄橙红四级）/ BATTERY_LOW / SIGNAL_WEAK */
    private String code;

    /** 阈值名称 */
    private String name;

    /** 阈值数值 */
    private Double value;

    /** 单位 */
    private String unit;

    /** 关联预警等级：BLUE / YELLOW / ORANGE / RED */
    private String level;

    /** 是否启用 */
    private Boolean enabled;
}
