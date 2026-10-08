package com.urbanflood.controller;

import com.urbanflood.common.PageResult;
import com.urbanflood.common.Result;
import com.urbanflood.dto.AlertDTO;
import com.urbanflood.entity.PushLog;
import com.urbanflood.service.AlertService;
import com.urbanflood.util.TimeUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 告警管理接口。
 */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public Result<PageResult<AlertDTO>> list(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "10") long size,
                                             @RequestParam(required = false) String level,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) Long stationId,
                                             @RequestParam(required = false) String start,
                                             @RequestParam(required = false) String end) {
        LocalDateTime s = TimeUtil.parseIso(start);
        LocalDateTime e = TimeUtil.parseIso(end);
        return Result.ok(alertService.page(level, status, stationId, s, e, page, size));
    }

    @GetMapping("/active")
    public Result<List<AlertDTO>> active() {
        return Result.ok(alertService.active());
    }

    @GetMapping("/statistics")
    public Result<Map<String, Long>> statistics() {
        return Result.ok(alertService.statistics());
    }

    @PutMapping("/{id}/handle")
    public Result<AlertDTO> handle(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.ok(alertService.handle(id, body.get("handler"), body.get("remark")));
    }

    @GetMapping("/{id}/pushes")
    public Result<List<PushLog>> pushes(@PathVariable Long id) {
        return Result.ok(alertService.pushes(id));
    }
}
