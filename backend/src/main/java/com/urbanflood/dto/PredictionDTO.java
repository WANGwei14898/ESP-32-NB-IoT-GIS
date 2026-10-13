package com.urbanflood.dto;

import com.urbanflood.entity.Prediction;
import com.urbanflood.util.JsonUtil;
import com.urbanflood.util.TimeUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 水位预测结果数据传输对象，与前端 Prediction 类型对应。
 */
@Data
@NoArgsConstructor
public class PredictionDTO {

    private Long id;
    private Long stationId;
    private String modelType;
    /** 预测生成时间（ISO-8601 字符串） */
    private String predictTime;
    private List<PredictionPoint> points;
    /** 预测 30 分钟后的水位（米） */
    private Double predicted30m;
    /** 预测 60 分钟后的水位（米） */
    private Double predicted60m;

    /**
     * 预测曲线上的单个点。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictionPoint {
        private String time;
        private Double value;
        private Double lower;
        private Double upper;
    }

    public static PredictionDTO from(Prediction p) {
        PredictionDTO dto = new PredictionDTO();
        dto.setId(p.getId());
        dto.setStationId(p.getStationId());
        dto.setModelType(p.getModelType());
        dto.setPredictTime(TimeUtil.formatIso(p.getPredictTime()));
        dto.setPoints(JsonUtil.parsePoints(p.getPoints()));
        // 从曲线点提取 30m / 60m 关键水位（步长固定 5 分钟：第 6 点=30m，第 12 点=60m）
        List<PredictionPoint> pts = dto.getPoints();
        if (pts != null && !pts.isEmpty()) {
            if (pts.size() >= 6) {
                dto.setPredicted30m(pts.get(5).getValue());
            }
            dto.setPredicted60m(pts.size() >= 12 ? pts.get(11).getValue() : pts.get(pts.size() - 1).getValue());
        }
        return dto;
    }
}
