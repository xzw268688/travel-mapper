package com.travelmapper.service.auth;

public interface EmailSender {

    void send(String to, String subject, String body);
}
