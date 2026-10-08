package com.urbanflood.controller;

import com.urbanflood.common.BusinessException;
import com.urbanflood.common.Result;
import com.urbanflood.dto.PredictionDTO;
import com.urbanflood.service.PredictionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 水位预测接口。
 */
@RestController
@RequestMapping("/api/prediction")
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @GetMapping
    public Result<PredictionDTO> get(@RequestParam Long stationId,
                                     @RequestParam(required = false) Integer minutes,
                                     @RequestParam(required = false) String model) {
        return Result.ok(predictionService.get(stationId, minutes, model));
    }

    @GetMapping("/models")
    public Result<List<String>> models() {
        return Result.ok(predictionService.models());
    }

    @PostMapping("/run")
    public Result<PredictionDTO> run(@RequestBody Map<String, Object> body) {
        Object stationIdObj = body.get("stationId");
        if (stationIdObj == null) {
            throw new BusinessException(400, "stationId 不能为空");
        }
        Long stationId = Long.valueOf(stationIdObj.toString());
        Integer minutes = body.get("minutes") == null ? null : Integer.valueOf(body.get("minutes").toString());
        String model = body.get("model") == null ? null : body.get("model").toString();
        return Result.ok(predictionService.run(stationId, minutes, model));
    }
}
