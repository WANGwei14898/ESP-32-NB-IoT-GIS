package com.urbanflood.controller;

import com.urbanflood.common.Result;
import com.urbanflood.entity.Threshold;
import com.urbanflood.service.ThresholdService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 阈值配置与协议配置接口。
 */
@RestController
public class ThresholdController {

    private final ThresholdService thresholdService;

    public ThresholdController(ThresholdService thresholdService) {
        this.thresholdService = thresholdService;
    }

    @GetMapping("/api/thresholds")
    public Result<List<Threshold>> list() {
        return Result.ok(thresholdService.list());
    }

    @PutMapping("/api/thresholds")
    public Result<Void> update(@RequestBody List<Threshold> thresholds) {
        thresholdService.update(thresholds);
        return Result.ok();
    }

    @GetMapping("/api/config/mqtt")
    public Result<Map<String, Object>> mqttConfig() {
        return Result.ok(thresholdService.mqttConfig());
    }

    @GetMapping("/api/config/coap")
    public Result<Map<String, Object>> coapConfig() {
        return Result.ok(thresholdService.coapConfig());
    }
}
