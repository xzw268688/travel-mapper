package com.travelmapper.service.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 安全通知：密码、邮箱与二次验证变更后给用户发信。
 * 通知是尽力而为，发送失败只记日志，不影响主流程。
 */
@Slf4j
@Service
public class SecurityNoticeService {

    private final EmailSender emailSender;

    public SecurityNoticeService(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    public void passwordChanged(String email) {
        send(email, "你的旅迹地图密码已修改",
                "你的账号密码刚刚被修改。如果这不是你本人操作，请立即使用「忘记密码」重置并联系管理员。");
    }

    public void emailChanged(String oldEmail, String newEmail) {
        send(oldEmail, "你的旅迹地图邮箱已修改",
                "你的账号邮箱已变更为 " + newEmail + "。如果这不是你本人操作，请立即联系管理员。");
    }

    public void twoFactorEnabled(String email) {
        send(email, "你的旅迹地图已开启二次验证",
                "你的账号已开启二次验证。请妥善保管恢复码；如果这不是你本人操作，请立即联系管理员。");
    }

    public void twoFactorDisabled(String email) {
        send(email, "你的旅迹地图已关闭二次验证",
                "你的账号已关闭二次验证。如果这不是你本人操作，请立即修改密码并联系管理员。");
    }

    public void recoveryCodesRegenerated(String email) {
        send(email, "你的旅迹地图恢复码已重新生成",
                "你的二次验证恢复码已重新生成，旧恢复码全部失效。如果这不是你本人操作，请立即联系管理员。");
    }

    public void twoFactorResetByAdmin(String email) {
        send(email, "你的旅迹地图二次验证已被重置",
                "管理员已重置你的二次验证。请重新登录并重新绑定认证器；如有疑问请联系管理员。");
    }

    private void send(String email, String subject, String body) {
        if (email == null || email.isBlank()) {
            return;
        }
        try {
            emailSender.send(email, subject, body);
        } catch (RuntimeException ex) {
            log.warn("security notice send failed subject={}: {}", subject, ex.toString());
        }
    }
}