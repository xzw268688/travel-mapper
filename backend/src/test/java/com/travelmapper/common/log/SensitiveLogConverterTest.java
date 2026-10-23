package com.travelmapper.common.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveLogConverterTest {

    @Test
    void converterMasksSecrets() {
        LoggerContext context = new LoggerContext();
        ch.qos.logback.classic.Logger logger = context.getLogger("test");
        LoggingEvent event = new LoggingEvent(
                "test", logger, Level.INFO,
                "login password=SuperSecret token=abc123 apiKey=sk-live-9999",
                null, null);
        String rendered = new SensitiveLogConverter().convert(event);
        assertThat(rendered).doesNotContain("SuperSecret", "abc123", "sk-live-9999");
        assertThat(rendered).contains("****");
    }
}
