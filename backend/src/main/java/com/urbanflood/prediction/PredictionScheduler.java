package com.urbanflood.prediction;

import com.urbanflood.entity.Station;
import com.urbanflood.mapper.StationMapper;
import com.urbanflood.service.PredictionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 预测定时任务：定期为所有站点计算水位预测并推送。
 */
@Slf4j
@Component
public class PredictionScheduler {

    private final StationMapper stationMapper;
    private final PredictionService predictionService;

    public PredictionScheduler(StationMapper stationMapper, PredictionService predictionService) {
        this.stationMapper = stationMapper;
        this.predictionService = predictionService;
    }

    @Scheduled(fixedDelayString = "${prediction.schedule-interval-ms:300000}")
    public void runPredictions() {
        List<Station> stations = stationMapper.selectList(null);
        if (stations.isEmpty()) {
            return;
        }
        log.info("开始定时水位预测，站点数: {}", stations.size());
        for (Station station : stations) {
            try {
                predictionService.run(station.getId(), null, null);
            } catch (Exception e) {
                log.warn("站点 {} 预测失败: {}", station.getCode(), e.getMessage());
            }
        }
    }
}
