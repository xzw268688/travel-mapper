package com.travelmapper.service.auth;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggingEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String body) {
        // Never log the body: it contains verification/reset tokens.
        log.info("email queued to={} subject={}", mask(to), subject);
    }

    private String mask(String email) {
        if (email == null) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
