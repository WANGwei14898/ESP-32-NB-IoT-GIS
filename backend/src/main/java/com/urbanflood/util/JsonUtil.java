package com.urbanflood.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbanflood.dto.PredictionDTO.PredictionPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON 工具类，基于 Jackson，提供对象与 JSON 字符串之间的转换。
 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtil() {
    }

    /** 解析 JSON 字符串为 JsonNode，失败返回 null。 */
    public static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /** 将对象序列化为 JSON 字符串。 */
    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }

    /** 解析预测曲线点 JSON 数组。 */
    public static List<PredictionPoint> parsePoints(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<PredictionPoint>>() {
            });
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    /** 将预测曲线点列表序列化为 JSON 字符串。 */
    public static String toPointsJson(List<PredictionPoint> points) {
        return toJson(points);
    }
}
