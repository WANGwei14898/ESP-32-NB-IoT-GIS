package com.urbanflood.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 时间工具类，负责遥测时间戳的解析与格式化。
 */
public final class TimeUtil {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private TimeUtil() {
    }

    /** 当前时间。 */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /**
     * 解析 ISO-8601 时间字符串（兼容带时区偏移，如 2026-10-08T12:00:00+08:00），
     * 统一转换为系统默认时区的 LocalDateTime，失败返回 null。
     */
    public static LocalDateTime parseIso(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.trim();
        try {
            if (t.contains("+") || t.endsWith("Z")) {
                return OffsetDateTime.parse(t).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            }
        } catch (Exception ignore) {
            // 回退到普通解析
        }
        try {
            String plain = t.length() > 19 ? t.substring(0, 19) : t;
            return LocalDateTime.parse(plain.replace(' ', 'T'), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }

    /** 格式化为 ISO-8601 字符串（无时区）。 */
    public static String formatIso(LocalDateTime time) {
        return time == null ? null : time.format(ISO);
    }
}
