package com.travelmapper.service.auth;

import com.travelmapper.common.config.AppProperties;
import com.travelmapper.service.config.ConfigKeys;
import com.travelmapper.service.config.ConfigService;
import com.travelmapper.service.config.ReloadableProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * 运行期可热加载的邮件发送器：DB 配置优先，未配置时回退环境变量。
 */
@Slf4j
@Service
public class MailSenderHolder implements EmailSender, ReloadableProvider {

    private final ConfigService configService;
    private final AppProperties properties;
    private volatile EmailSender current = new LoggingEmailSender();
    private volatile boolean smtpEnabled;

    public MailSenderHolder(ConfigService configService, AppProperties properties) {
        this.configService = configService;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try {
            reload();
        } catch (RuntimeException ex) {
            log.warn("mail sender not reloaded at startup: {}", ex.getMessage());
            current = new LoggingEmailSender();
            smtpEnabled = false;
        }
    }

    @Override
    public synchronized void reload() {
        AppProperties.Mail fallback = properties.getMail();
        boolean enabled = configService.getBoolean(ConfigKeys.MAIL_ENABLED, fallback.isEnabled());
        String host = configService.get(ConfigKeys.MAIL_HOST, fallback.getHost());
        int port = configService.getInt(ConfigKeys.MAIL_PORT, fallback.getPort());
        String username = configService.get(ConfigKeys.MAIL_USERNAME, fallback.getUsername());
        String password = configService.get(ConfigKeys.MAIL_PASSWORD, fallback.getPassword());
        String from = configService.get(ConfigKeys.MAIL_FROM, fallback.getFrom());
        boolean auth = configService.getBoolean(ConfigKeys.MAIL_SMTP_AUTH, fallback.isSmtpAuth());
        boolean starttls = configService.getBoolean(
                ConfigKeys.MAIL_SMTP_STARTTLS, fallback.isSmtpStarttls());

        if (!enabled || isBlank(host) || isBlank(from)) {
            current = new LoggingEmailSender();
            smtpEnabled = false;
            return;
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setDefaultEncoding("UTF-8");
        if (!isBlank(username)) {
            sender.setUsername(username);
        }
        if (password != null) {
            sender.setPassword(password);
        }
        Properties mailProperties = sender.getJavaMailProperties();
        mailProperties.put("mail.smtp.auth", String.valueOf(auth));
        mailProperties.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        mailProperties.put("mail.smtp.starttls.required", String.valueOf(starttls));
        // 防止 SMTP 不可达时请求无限等待；10 秒内必须完成连接/读取/写入。
        mailProperties.put("mail.smtp.connectiontimeout", "10000");
        mailProperties.put("mail.smtp.timeout", "10000");
        mailProperties.put("mail.smtp.writetimeout", "10000");
        // 允许 JavaMail 在 TLSv1.2/TLSv1.3 间协商。
        mailProperties.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        // 不显式指定时 JavaMail 用 InetAddress.getLocalHost().getCanonicalHostName()
        // 生成 EHLO 域名，部分网络下反向 DNS 查询会卡十几秒，直接拖垮发送超时。
        mailProperties.put("mail.smtp.localhost", ehloHost(from));
        current = new SmtpEmailSender(sender, from);
        smtpEnabled = true;
        log.info("mail sender reloaded host={} port={} auth={} starttls={}",
                host, port, auth, starttls);
    }

    @Override
    public void send(String to, String subject, String body) {
        current.send(to, subject, body);
    }

    public boolean isSmtpEnabled() {
        return smtpEnabled;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** EHLO 域名取发件人域；取不到时用 localhost（服务端通常不校验）。 */
    private String ehloHost(String from) {
        int at = from.lastIndexOf('@');
        if (at < 0 || at == from.length() - 1) {
            return "localhost";
        }
        String domain = from.substring(at + 1).replaceAll("[<>\\s]", "");
        return domain.isEmpty() ? "localhost" : domain;
    }
}