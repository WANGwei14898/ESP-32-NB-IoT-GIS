package com.urbanflood.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警推送记录实体，对应 push_log 表。
 */
@Data
@TableName("push_log")
public class PushLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联告警 ID */
    private Long alertId;

    /** 推送渠道：SMS / WECHAT / EMAIL / APP / WEBHOOK / WEBSOCKET */
    private String channel;

    /** 推送目标 */
    private String target;

    /** 推送内容 */
    private String content;

    /** 推送状态：SUCCESS / FAILED */
    private String status;

    /** 推送时间 */
    private LocalDateTime pushTime;
}
