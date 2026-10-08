package com.urbanflood.controller;

import com.urbanflood.common.PageResult;
import com.urbanflood.common.Result;
import com.urbanflood.entity.Device;
import com.urbanflood.service.DeviceService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 设备管理接口。
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    public Result<PageResult<Device>> list(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "10") long size,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String status) {
        return Result.ok(deviceService.page(keyword, status, page, size));
    }

    @GetMapping("/stats")
    public Result<Map<String, Long>> stats() {
        return Result.ok(deviceService.stats());
    }

    @GetMapping("/{id}")
    public Result<Device> get(@PathVariable Long id) {
        return Result.ok(deviceService.getById(id));
    }

    @PostMapping
    public Result<Device> create(@RequestBody Device device) {
        return Result.ok(deviceService.create(device));
    }

    @PutMapping("/{id}")
    public Result<Device> update(@PathVariable Long id, @RequestBody Device device) {
        return Result.ok(deviceService.update(id, device));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return Result.ok();
    }
}
