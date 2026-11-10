package com.travelmapper.common.time;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 全系统唯一的“当前时间”来源。业务代码禁止直接调用 LocalDate.now() / LocalDateTime.now()，
 * 否则行为会随 JVM 默认时区漂移。
 */
@Component
public class TimeProvider {

    public static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    private final Clock clock;

    public TimeProvider(Clock clock) {
        this.clock = clock;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public ZoneId zone() {
        return clock.getZone();
    }
}