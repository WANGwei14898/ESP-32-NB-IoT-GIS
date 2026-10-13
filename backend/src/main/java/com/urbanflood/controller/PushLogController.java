package com.urbanflood.controller;

import com.urbanflood.common.PageResult;
import com.urbanflood.common.Result;
import com.urbanflood.entity.PushLog;
import com.urbanflood.service.PushLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 推送记录查询接口，供前端“通知中心”展示全部推送历史。
 */
@RestController
@RequestMapping("/api/push-logs")
public class PushLogController {

    private final PushLogService pushLogService;

    public PushLogController(PushLogService pushLogService) {
        this.pushLogService = pushLogService;
    }

    /**
     * 分页查询推送历史。
     */
    @GetMapping
    public Result<PageResult<PushLog>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "20") long size,
                                            @RequestParam(required = false) String channel,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) Long alertId) {
        return Result.ok(pushLogService.page(channel, status, alertId, page, size));
    }

    /**
     * 推送统计：总数 / 成功数 / 失败数。
     */
    @GetMapping("/statistics")
    public Result<Map<String, Long>> statistics() {
        return Result.ok(pushLogService.statistics());
    }
}
