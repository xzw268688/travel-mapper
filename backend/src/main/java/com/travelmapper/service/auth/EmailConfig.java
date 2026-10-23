package com.travelmapper.service.auth;

import com.travelmapper.common.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class EmailConfig {

    @Bean
    @ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
    public EmailSender loggingEmailSender() {
        return new LoggingEmailSender();
    }

    @Bean
    @ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
    public EmailSender smtpEmailSender(JavaMailSender mailSender, AppProperties properties) {
        return new SmtpEmailSender(mailSender, properties.getMail().getFrom());
    }
}
