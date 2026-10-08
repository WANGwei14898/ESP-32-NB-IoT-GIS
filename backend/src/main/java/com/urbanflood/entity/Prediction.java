package com.urbanflood.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 水位预测结果实体，对应 prediction 表。
 * points 字段为 JSON 字符串，格式：[{time, value, lower, upper}, ...]。
 */
@Data
@TableName("prediction")
public class Prediction {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 站点 ID */
    private Long stationId;

    /** 预测模型类型：moving_average / arima / lstm */
    private String modelType;

    /** 预测生成时间 */
    private LocalDateTime predictTime;

    /** 预测曲线点（JSON 字符串） */
    private String points;
}
