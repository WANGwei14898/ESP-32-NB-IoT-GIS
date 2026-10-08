package com.urbanflood.controller;

import com.urbanflood.common.Result;
import com.urbanflood.dto.GisDTO.HeatPoint;
import com.urbanflood.dto.GisDTO.InfluenceCircle;
import com.urbanflood.dto.GisDTO.StationView;
import com.urbanflood.dto.GisDTO.WaterPoint;
import com.urbanflood.service.GisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GIS 地图数据接口。
 */
@RestController
@RequestMapping("/api/gis")
public class GisController {

    private final GisService gisService;

    public GisController(GisService gisService) {
        this.gisService = gisService;
    }

    @GetMapping("/stations")
    public Result<List<StationView>> stations() {
        return Result.ok(gisService.stations());
    }

    @GetMapping("/water-points")
    public Result<List<WaterPoint>> waterPoints() {
        return Result.ok(gisService.waterPoints());
    }

    @GetMapping("/influence")
    public Result<List<InfluenceCircle>> influence() {
        return Result.ok(gisService.influence());
    }

    @GetMapping("/heatmap")
    public Result<List<HeatPoint>> heatmap() {
        return Result.ok(gisService.heatmap());
    }
}
