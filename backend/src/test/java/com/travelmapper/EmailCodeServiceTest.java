package com.travelmapper;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.config.AppProperties;
import com.travelmapper.service.auth.EmailCodeService;
import com.travelmapper.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailCodeServiceTest extends AbstractIntegrationTest {

    private static final Pattern DEV_CODE = Pattern.compile("code=(\\d{6})");

    @Autowired
    private EmailCodeService emailCodeService;

    @Autowired
    private AppProperties properties;

    private Logger codeLogger;
    private ListAppender<ILoggingEvent> appender;
    private boolean originalMailEnabled;

    @BeforeEach
    void attachLogAppender() {
        originalMailEnabled = properties.getMail().isEnabled();
        codeLogger = (Logger) LoggerFactory.getLogger(EmailCodeService.class);
        appender = new ListAppender<>();
        appender.start();
        codeLogger.addAppender(appender);
    }

    @AfterEach
    void detachLogAppender() {
        properties.getMail().setEnabled(originalMailEnabled);
        if (codeLogger != null && appender != null) {
            codeLogger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void devModeLogsCodeButProductionDoesNot() {
        String email = uniq() + "@example.com";

        properties.getMail().setEnabled(false);
        emailCodeService.issue(email, EmailCodeService.Scene.VERIFY);
        String logged = devCodeFromLog(email);
        assertThat(logged).hasSize(6);

        appender.list.clear();
        properties.getMail().setEnabled(true);
        emailCodeService.issue(email, EmailCodeService.Scene.RESET);
        assertThat(devCodeFromLog(email)).isNull();
        assertThat(capturingMail.lastCode(email)).isNotNull();
    }

    @Test
    void issuingNewCodeInvalidatesThePreviousOne() {
        String email = uniq() + "@example.com";
        properties.getMail().setEnabled(true);

        emailCodeService.issue(email, EmailCodeService.Scene.VERIFY);
        String first = capturingMail.lastCode(email);
        emailCodeService.issue(email, EmailCodeService.Scene.VERIFY);
        String second = capturingMail.lastCode(email);

        assertThat(first).isNotEqualTo(second);
        assertThatThrownBy(() -> emailCodeService.verify(email, EmailCodeService.Scene.VERIFY, first))
                .isInstanceOf(BusinessException.class);
        emailCodeService.verify(email, EmailCodeService.Scene.VERIFY, second);
    }

    @Test
    void codeIsConsumedOnceAndRemoved() {
        String email = uniq() + "@example.com";
        properties.getMail().setEnabled(true);
        emailCodeService.issue(email, EmailCodeService.Scene.RESET);
        String code = capturingMail.lastCode(email);

        emailCodeService.verify(email, EmailCodeService.Scene.RESET, code);
        assertThatThrownBy(() -> emailCodeService.verify(email, EmailCodeService.Scene.RESET, code))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void concurrentWrongAttemptsCannotExceedTheAttemptLimit() throws Exception {
        String email = uniq() + "@example.com";
        properties.getMail().setEnabled(true);
        emailCodeService.issue(email, EmailCodeService.Scene.VERIFY);
        String code = capturingMail.lastCode(email);
        String wrong = code.equals("000000") ? "111111" : "000000";

        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> {
                try {
                    emailCodeService.verify(email, EmailCodeService.Scene.VERIFY, wrong);
                    return true;
                } catch (BusinessException ex) {
                    return false;
                }
            });
        }
        List<Future<Boolean>> results = pool.invokeAll(tasks);
        for (Future<Boolean> result : results) {
            assertThat(result.get()).isFalse();
        }
        pool.shutdownNow();

        assertThatThrownBy(() -> emailCodeService.verify(email, EmailCodeService.Scene.VERIFY, code))
                .isInstanceOf(BusinessException.class);
    }

    private String devCodeFromLog(String email) {
        for (ILoggingEvent event : appender.list) {
            String message = event.getFormattedMessage();
            if (message.contains("[auth-dev]") && message.contains(email)) {
                Matcher matcher = DEV_CODE.matcher(message);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }
        return null;
    }

    private String uniq() {
        return "code_" + UUID.randomUUID().toString().substring(0, 8);
    }
}