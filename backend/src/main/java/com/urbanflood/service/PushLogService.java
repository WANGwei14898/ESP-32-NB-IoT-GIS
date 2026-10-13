package com.urbanflood.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.urbanflood.common.PageResult;
import com.urbanflood.entity.PushLog;
import com.urbanflood.mapper.PushLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 告警推送记录服务，提供推送历史的分页查询与统计，供前端“通知中心”使用。
 */
@Service
public class PushLogService {

    private final PushLogMapper pushLogMapper;

    public PushLogService(PushLogMapper pushLogMapper) {
        this.pushLogMapper = pushLogMapper;
    }

    /**
     * 分页查询推送记录，支持按渠道、状态、告警 ID 筛选，按推送时间倒序。
     *
     * @param channel 推送渠道：SMS / WECHAT / EMAIL / APP / WEBSOCKET
     * @param status  推送状态：SUCCESS / FAILED
     * @param alertId 关联告警 ID（可选）
     */
    public PageResult<PushLog> page(String channel, String status, Long alertId, long page, long size) {
        LambdaQueryWrapper<PushLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(channel)) {
            wrapper.eq(PushLog::getChannel, channel);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(PushLog::getStatus, status);
        }
        if (alertId != null) {
            wrapper.eq(PushLog::getAlertId, alertId);
        }
        wrapper.orderByDesc(PushLog::getPushTime);
        Page<PushLog> result = pushLogMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), page, size);
    }

    /**
     * 推送统计：总数 / 成功数 / 失败数。
     */
    public Map<String, Long> statistics() {
        long total = pushLogMapper.selectCount(null);
        long success = pushLogMapper.selectCount(
                new LambdaQueryWrapper<PushLog>().eq(PushLog::getStatus, "SUCCESS"));
        long failed = pushLogMapper.selectCount(
                new LambdaQueryWrapper<PushLog>().eq(PushLog::getStatus, "FAILED"));
        return Map.of("total", total, "success", success, "failed", failed);
    }
}
