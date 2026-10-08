package com.urbanflood.dto;

import com.urbanflood.entity.Alert;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 告警数据传输对象，与前端 Alert 类型对应。
 */
@Data
@NoArgsConstructor
public class AlertDTO {

    private Long id;
    private Long stationId;
    private String stationName;
    /** 预警等级：BLUE / YELLOW / ORANGE / RED */
    private String level;
    /** 告警类型 */
    private String type;
    private String message;
    private Double waterLevel;
    private Double threshold;
    /** 处理状态：ACTIVE / HANDLED */
    private String status;
    private String handler;
    private String remark;
    private LocalDateTime handledTime;
    private LocalDateTime createTime;

    public static AlertDTO from(Alert a) {
        AlertDTO dto = new AlertDTO();
        dto.setId(a.getId());
        dto.setStationId(a.getStationId());
        dto.setStationName(a.getStationName());
        dto.setLevel(a.getLevel());
        dto.setType(a.getType());
        dto.setMessage(a.getMessage());
        dto.setWaterLevel(a.getWaterLevel());
        dto.setThreshold(a.getThreshold());
        dto.setStatus(a.getStatus());
        dto.setHandler(a.getHandler());
        dto.setRemark(a.getRemark());
        dto.setHandledTime(a.getHandledTime());
        dto.setCreateTime(a.getCreateTime());
        return dto;
    }
}
