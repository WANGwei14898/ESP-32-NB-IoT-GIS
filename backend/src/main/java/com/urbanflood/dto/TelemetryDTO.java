package com.urbanflood.dto;

import com.urbanflood.entity.Telemetry;
import com.urbanflood.util.TimeUtil;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 遥测数据传输对象，与前端 Telemetry 类型对应。
 */
@Data
@NoArgsConstructor
public class TelemetryDTO {

    private Long id;
    private Long stationId;
    private Long deviceId;
    private String stationCode;
    private String stationName;
    private Double waterLevel;
    private Double rainfall;
    private Double flowVelocity;
    private Double battery;
    private Double signalStrength;
    /** 采集时间（ISO-8601 字符串） */
    private String timestamp;

    public static TelemetryDTO from(Telemetry t) {
        TelemetryDTO dto = new TelemetryDTO();
        dto.setId(t.getId());
        dto.setStationId(t.getStationId());
        dto.setDeviceId(t.getDeviceId());
        dto.setWaterLevel(t.getWaterLevel());
        dto.setRainfall(t.getRainfall());
        dto.setFlowVelocity(t.getFlowVelocity());
        dto.setBattery(t.getBattery());
        dto.setSignalStrength(t.getSignalStrength());
        dto.setTimestamp(TimeUtil.formatIso(t.getTs()));
        return dto;
    }
}
