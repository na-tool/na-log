package com.na.syslog.utils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 时间相关的内置工具，取代原先对 na-common 的依赖。
 */
public final class DateTimeUtils {

    /** 北京时间，固定写死避免依赖宿主的默认时区 */
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    private DateTimeUtils() {
    }

    /**
     * 取北京时区的当前时间。
     *
     * @return 无时区的 +8 墙上时钟时间
     */
    public static LocalDateTime getCurrentBeijingDateTime() {
        // ZonedDateTime 取北京时区墙钟，转 LocalDateTime 后即无时区的 +8 时间
        return ZonedDateTime.now(BEIJING).toLocalDateTime();
    }

    /**
     * 计算两个时间点之间的毫秒差。
     *
     * @param start 起始时间
     * @param end   结束时间
     * @return 毫秒差；任一参数为 null 时返回 null
     */
    public static Long calculateTimeDifference(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return null;
        }
        return Duration.between(start, end).toMillis();
    }
}
