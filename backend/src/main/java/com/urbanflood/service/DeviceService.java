package com.urbanflood.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.urbanflood.common.BusinessException;
import com.urbanflood.common.PageResult;
import com.urbanflood.entity.Device;
import com.urbanflood.entity.Station;
import com.urbanflood.mapper.DeviceMapper;
import com.urbanflood.mapper.StationMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 设备管理服务。
 */
@Service
public class DeviceService {

    private final DeviceMapper deviceMapper;
    private final StationMapper stationMapper;

    public DeviceService(DeviceMapper deviceMapper, StationMapper stationMapper) {
        this.deviceMapper = deviceMapper;
        this.stationMapper = stationMapper;
    }

    /** 分页查询设备列表。 */
    public PageResult<Device> page(String keyword, String status, long page, long size) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Device::getCode, keyword).or().like(Device::getName, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Device::getStatus, status);
        }
        wrapper.orderByDesc(Device::getId);
        Page<Device> result = deviceMapper.selectPage(new Page<>(page, size), wrapper);
        fillStationNames(result.getRecords());
        return new PageResult<>(result.getRecords(), result.getTotal(), page, size);
    }

    /** 查询设备详情。 */
    public Device getById(Long id) {
        Device device = deviceMapper.selectById(id);
        if (device == null) {
            throw new BusinessException(404, "设备不存在");
        }
        fillStationNames(List.of(device));
        return device;
    }

    /** 设备状态统计。 */
    public Map<String, Long> stats() {
        long total = deviceMapper.selectCount(null);
        long online = countByStatus("ONLINE");
        long offline = countByStatus("OFFLINE");
        long fault = countByStatus("FAULT");
        return Map.of("total", total, "online", online, "offline", offline, "fault", fault);
    }

    public Device create(Device device) {
        if (device.getInstallTime() == null) {
            device.setInstallTime(LocalDateTime.now());
        }
        if (device.getStatus() == null) {
            device.setStatus("OFFLINE");
        }
        deviceMapper.insert(device);
        return device;
    }

    public Device update(Long id, Device device) {
        device.setId(id);
        deviceMapper.updateById(device);
        return getById(id);
    }

    public void delete(Long id) {
        deviceMapper.deleteById(id);
    }

    private long countByStatus(String status) {
        return deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getStatus, status));
    }

    private void fillStationNames(List<Device> devices) {
        if (devices == null || devices.isEmpty()) {
            return;
        }
        List<Long> ids = devices.stream().map(Device::getStationId)
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = stationMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Station::getId, Station::getName));
        devices.forEach(d -> {
            if (d.getStationId() != null) {
                d.setStationName(nameMap.get(d.getStationId()));
            }
        });
    }
}
