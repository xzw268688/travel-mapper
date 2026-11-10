package com.travelmapper.controller.admin.dto;

import lombok.Data;

@Data
public class MailConfigDto {

    private Boolean enabled;
    private String host;
    private Integer port;
    private String username;
    private String password;
    private String from;
    private Boolean smtpAuth;
    private Boolean starttls;
}