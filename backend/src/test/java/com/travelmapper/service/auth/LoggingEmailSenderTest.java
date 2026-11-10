package com.travelmapper.service.auth;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingEmailSenderTest {

    @Test
    void doesNotLogVerificationTokenOrFullEmail() {
        Logger logger = (Logger) LoggerFactory.getLogger(LoggingEmailSender.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new LoggingEmailSender().send("alice@example.com", "验证邮箱", "token=SuperSecretToken");
        } finally {
            logger.detachAppender(appender);
        }
        String logs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(logs).doesNotContain("SuperSecretToken");
        assertThat(logs).doesNotContain("alice@example.com");
        assertThat(logs).contains("a***@example.com");
    }
}
