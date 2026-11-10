package com.travelmapper.common.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfig {

    /**
     * 固定北京时间。Clock 显式带时区，因此即使 JVM 以 UTC 启动，
     * LocalDate.now(clock) / LocalDateTime.now(clock) 仍返回北京时间墙钟。
     */
    @Bean
    public Clock clock() {
        return Clock.system(TimeProvider.BEIJING);
    }
}