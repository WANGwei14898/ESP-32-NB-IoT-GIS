package com.urbanflood.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.urbanflood.dto.PredictionDTO;
import com.urbanflood.dto.PredictionDTO.PredictionPoint;
import com.urbanflood.entity.Prediction;
import com.urbanflood.entity.Station;
import com.urbanflood.entity.Telemetry;
import com.urbanflood.mapper.PredictionMapper;
import com.urbanflood.mapper.StationMapper;
import com.urbanflood.mapper.TelemetryMapper;
import com.urbanflood.util.JsonUtil;
import com.urbanflood.util.TimeUtil;
import com.urbanflood.websocket.RealtimePushHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 水位预测服务：调用 Python 预测服务，失败时回退到本地移动平均预测。
 */
@Slf4j
@Service
public class PredictionService {

    private static final List<String> MODELS = List.of("moving_average", "arima", "lstm");

    private final PredictionMapper predictionMapper;
    private final TelemetryMapper telemetryMapper;
    private final StationMapper stationMapper;
    private final RealtimePushHandler realtimePushHandler;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${prediction.base-url:http://localhost:8000}")
    private String baseUrl;

    @Value("${prediction.default-model:moving_average}")
    private String defaultModel;

    @Value("${prediction.default-minutes:60}")
    private int defaultMinutes;

    public PredictionService(PredictionMapper predictionMapper,
                             TelemetryMapper telemetryMapper,
                             StationMapper stationMapper,
                             RealtimePushHandler realtimePushHandler) {
        this.predictionMapper = predictionMapper;
        this.telemetryMapper = telemetryMapper;
        this.stationMapper = stationMapper;
        this.realtimePushHandler = realtimePushHandler;
    }

    public PredictionDTO get(Long stationId, Integer minutes, String model) {
        Prediction latest = predictionMapper.selectLatestByStation(stationId);
        if (latest == null) {
            return run(stationId, minutes, model);
        }
        return PredictionDTO.from(latest);
    }

    public List<String> models() {
        return MODELS;
    }

    public PredictionDTO run(Long stationId, Integer minutes, String model) {
        int minutesValue = minutes == null || minutes <= 0 ? defaultMinutes : minutes;
        String modelValue = model == null || model.isBlank() ? defaultModel : model;

        List<Telemetry> history = telemetryMapper.selectRecent(stationId, 60);
        List<PredictionPoint> points;
        try {
            points = callPython(stationId, history, minutesValue, modelValue);
        } catch (Exception e) {
            log.warn("调用 Python 预测服务失败，回退本地预测: {}", e.getMessage());
            points = localForecast(history, minutesValue);
        }

        Prediction entity = new Prediction();
        entity.setStationId(stationId);
        entity.setModelType(modelValue);
        entity.setPredictTime(LocalDateTime.now());
        entity.setPoints(JsonUtil.toPointsJson(points));
        predictionMapper.insert(entity);

        PredictionDTO dto = PredictionDTO.from(entity);
        realtimePushHandler.pushPrediction(dto);
        return dto;
    }

    private List<PredictionPoint> callPython(Long stationId, List<Telemetry> history, int minutes, String model) {
        Station station = stationMapper.selectById(stationId);
        String stationCode = station == null ? String.valueOf(stationId) : station.getCode();

        List<Map<String, Object>> hist = new ArrayList<>();
        for (Telemetry t : history) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("time", TimeUtil.formatIso(t.getTs()));
            p.put("value", t.getWaterLevel());
            hist.add(p);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("station_id", stationCode);
        body.put("stationId", stationId);
        body.put("minutes", minutes);
        body.put("model", model);
        body.put("history", hist);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(JsonUtil.toJson(body), headers);
        String response = restTemplate.postForObject(baseUrl + "/predict", request, String.class);

        JsonNode node = JsonUtil.parse(response);
        List<PredictionPoint> points = new ArrayList<>();
        if (node != null && node.hasNonNull("points")) {
            for (JsonNode p : node.get("points")) {
                PredictionPoint point = new PredictionPoint();
                point.setTime(p.hasNonNull("time") ? p.get("time").asText() : null);
                point.setValue(p.hasNonNull("value") ? p.get("value").asDouble() : null);
                point.setLower(p.hasNonNull("lower") ? p.get("lower").asDouble() : null);
                point.setUpper(p.hasNonNull("upper") ? p.get("upper").asDouble() : null);
                points.add(point);
            }
        }
        if (points.isEmpty()) {
            throw new RuntimeException("Python 预测服务返回空结果");
        }
        return points;
    }
    /** 本地移动平均 / 线性外推预测。 */
    private List<PredictionPoint> localForecast(List<Telemetry> history, int minutes) {
        List<Double> values = new ArrayList<>();
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).getWaterLevel() != null) {
                values.add(history.get(i).getWaterLevel());
            }
        }
        if (values.isEmpty()) {
            values.add(0.3);
        }
        double last = values.get(0);
        double slope = 0.0;
        if (values.size() >= 2) {
            double sum = 0.0;
            int n = Math.min(values.size(), 10);
            for (int i = 0; i < n - 1; i++) {
                sum += (values.get(i) - values.get(i + 1));
            }
            slope = sum / Math.max(1, n - 1);
        }

        List<PredictionPoint> points = new ArrayList<>();
        int stepMinutes = 5;
        int count = minutes / stepMinutes;
        LocalDateTime now = LocalDateTime.now();
        for (int i = 1; i <= count; i++) {
            double value = Math.max(0.0, last + slope * i);
            LocalDateTime t = now.plusMinutes((long) stepMinutes * i);
            PredictionPoint point = new PredictionPoint();
            point.setTime(TimeUtil.formatIso(t));
            point.setValue(round(value));
            point.setLower(round(value * 0.95));
            point.setUpper(round(value * 1.05));
            points.add(point);
        }
        return points;
    }

    private double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
