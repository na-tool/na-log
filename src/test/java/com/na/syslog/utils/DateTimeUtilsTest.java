package com.na.syslog.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DateTimeUtilsTest {

    @Test
    @DisplayName("取到的是北京时区的当前时间")
    void returnsBeijingNow() {
        LocalDateTime actual = DateTimeUtils.getCurrentBeijingDateTime();
        LocalDateTime expected = ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).toLocalDateTime();

        assertThat(Math.abs(ChronoUnit.SECONDS.between(actual, expected)))
                .isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("时间差按毫秒计算")
    void calculatesDifferenceInMillis() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        LocalDateTime end = start.plusSeconds(2).plusNanos(500_000_000L);

        assertThat(DateTimeUtils.calculateTimeDifference(start, end)).isEqualTo(2500L);
    }

    @Test
    @DisplayName("任一参数为 null 时返回 null，而不是抛异常")
    void isNullSafe() {
        LocalDateTime now = DateTimeUtils.getCurrentBeijingDateTime();

        assertThat(DateTimeUtils.calculateTimeDifference(null, now)).isNull();
        assertThat(DateTimeUtils.calculateTimeDifference(now, null)).isNull();
        assertThat(DateTimeUtils.calculateTimeDifference(null, null)).isNull();
    }
}
