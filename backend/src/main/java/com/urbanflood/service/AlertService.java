package com.urbanflood.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.urbanflood.common.BusinessException;
import com.urbanflood.common.PageResult;
import com.urbanflood.dto.AlertDTO;
import com.urbanflood.entity.Alert;
import com.urbanflood.entity.PushLog;
import com.urbanflood.entity.Station;
import com.urbanflood.mapper.AlertMapper;
import com.urbanflood.mapper.PushLogMapper;
import com.urbanflood.mapper.StationMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 告警管理服务。
 */
@Service
public class AlertService {

    private final AlertMapper alertMapper;
    private final PushLogMapper pushLogMapper;
    private final StationMapper stationMapper;

    public AlertService(AlertMapper alertMapper, PushLogMapper pushLogMapper, StationMapper stationMapper) {
        this.alertMapper = alertMapper;
        this.pushLogMapper = pushLogMapper;
        this.stationMapper = stationMapper;
    }

    /** 分页查询告警。 */
    public PageResult<AlertDTO> page(String level, String status, Long stationId,
                                     LocalDateTime start, LocalDateTime end, long page, long size) {
        LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(level)) {
            wrapper.eq(Alert::getLevel, level);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Alert::getStatus, status);
        }
        if (stationId != null) {
            wrapper.eq(Alert::getStationId, stationId);
        }
        if (start != null) {
            wrapper.ge(Alert::getCreateTime, start);
        }
        if (end != null) {
            wrapper.le(Alert::getCreateTime, end);
        }
        wrapper.orderByDesc(Alert::getCreateTime);
        Page<Alert> result = alertMapper.selectPage(new Page<>(page, size), wrapper);
        List<AlertDTO> records = result.getRecords().stream()
                .map(a -> enrich(AlertDTO.from(a)))
                .collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), page, size);
    }

    /** 查询未处理告警。 */
    public List<AlertDTO> active() {
        List<Alert> list = alertMapper.selectList(
                new LambdaQueryWrapper<Alert>().eq(Alert::getStatus, "ACTIVE")
                        .orderByDesc(Alert::getCreateTime));
        return list.stream().map(a -> enrich(AlertDTO.from(a))).collect(Collectors.toList());
    }

    /** 告警统计。 */
    public Map<String, Long> statistics() {
        long total = alertMapper.selectCount(null);
        long active = alertMapper.selectCount(new LambdaQueryWrapper<Alert>().eq(Alert::getStatus, "ACTIVE"));
        long handled = alertMapper.selectCount(new LambdaQueryWrapper<Alert>().eq(Alert::getStatus, "HANDLED"));
        return Map.of(
                "total", total,
                "active", active,
                "handled", handled,
                "blue", countByLevel("BLUE"),
                "yellow", countByLevel("YELLOW"),
                "orange", countByLevel("ORANGE"),
                "red", countByLevel("RED"));
    }

    /** 处理告警。 */
    public AlertDTO handle(Long id, String handler, String remark) {
        Alert alert = alertMapper.selectById(id);
        if (alert == null) {
            throw new BusinessException(404, "告警不存在");
        }
        alert.setStatus("HANDLED");
        alert.setHandler(handler);
        alert.setRemark(remark);
        alert.setHandledTime(LocalDateTime.now());
        alertMapper.updateById(alert);
        return enrich(AlertDTO.from(alert));
    }

    /** 查询告警推送记录。 */
    public List<PushLog> pushes(Long alertId) {
        return pushLogMapper.selectList(
                new LambdaQueryWrapper<PushLog>().eq(PushLog::getAlertId, alertId)
                        .orderByAsc(PushLog::getPushTime));
    }

    private long countByLevel(String level) {
        return alertMapper.selectCount(new LambdaQueryWrapper<Alert>().eq(Alert::getLevel, level));
    }

    private AlertDTO enrich(AlertDTO dto) {
        if (dto.getStationId() != null) {
            Station station = stationMapper.selectById(dto.getStationId());
            if (station != null) {
                dto.setStationName(station.getName());
            }
        }
        return dto;
    }
}
