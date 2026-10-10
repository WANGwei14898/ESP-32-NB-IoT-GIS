package com.urbanflood.controller;

import com.urbanflood.common.Result;
import com.urbanflood.dto.TelemetryDTO;
import com.urbanflood.service.TelemetryService;
import com.urbanflood.util.TimeUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 遥测数据查询接口。
 */
@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {

    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    /** 查询最新遥测（指定站点返回单条，否则返回全部站点最新）。 */
    @GetMapping("/latest")
    public Result<Object> latest(@RequestParam(required = false) Long stationId) {
        if (stationId != null) {
            return Result.ok(telemetryService.latest(stationId));
        }
        return Result.ok(telemetryService.realtime());
    }

    /** 直接响应 GET /api/telemetry?stationId=xxx（stationId 可为站点编号 S001 或数字主键 id）。 */
    @GetMapping
    public Result<Object> query(@RequestParam(required = false) String stationId) {
        if (stationId == null || stationId.isBlank()) {
            return Result.ok(telemetryService.realtime());
        }
        return Result.ok(telemetryService.latestByStationKey(stationId));
    }

    /** 查询历史遥测序列。 */
    @GetMapping("/history")
    public Result<List<TelemetryDTO>> history(@RequestParam(required = false) Long stationId,
                                              @RequestParam(required = false) String start,
                                              @RequestParam(required = false) String end,
                                              @RequestParam(defaultValue = "1000") int limit) {
        if (stationId == null) {
            return Result.ok(List.of());
        }
        LocalDateTime s = TimeUtil.parseIso(start);
        LocalDateTime e = TimeUtil.parseIso(end);
        if (s == null) {
            s = LocalDateTime.now().minusHours(24);
        }
        if (e == null) {
            e = LocalDateTime.now();
        }
        return Result.ok(telemetryService.history(stationId, s, e, limit));
    }

    /** 查询全部站点实时快照。 */
    @GetMapping("/realtime")
    public Result<List<TelemetryDTO>> realtime() {
        return Result.ok(telemetryService.realtime());
    }
}
