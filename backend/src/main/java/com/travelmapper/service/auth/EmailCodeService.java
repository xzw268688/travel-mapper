package com.travelmapper.service.auth;

import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.common.config.AppProperties;
import com.travelmapper.common.ratelimit.RedisRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;

/**
 * 邮箱验证码：只存 HMAC 摘要，10 分钟有效，5 次试错，重发覆盖。
 * 6 位码空间小，哈希本身不是安全边界；真正的防线是 TTL、试错上限与发送限流。
 */
@Slf4j
@Service
public class EmailCodeService {

    public enum Scene {
        VERIFY,
        RESET
    }

    private static final int CODE_TTL_SECONDS = 600;
    private static final int MAX_ATTEMPTS = 5;
    private static final long RESEND_GAP_SECONDS = 60;
    private static final long DAILY_LIMIT = 10;
    private static final long IP_HOURLY_LIMIT = 20;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private static final DefaultRedisScript<Long> VERIFY_SCRIPT = new DefaultRedisScript<>(
            "local digest = redis.call('HGET', KEYS[1], 'digest') "
                    + "if not digest then return 0 end "
                    + "if digest == ARGV[1] then redis.call('DEL', KEYS[1]) return 1 end "
                    + "local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1) "
                    + "if attempts >= tonumber(ARGV[2]) then redis.call('DEL', KEYS[1]) end "
                    + "return 0",
            Long.class);

    private final StringRedisTemplate redis;
    private final RedisRateLimiter rateLimiter;
    private final EmailSender emailSender;
    private final MailSenderHolder mailSenderHolder;
    private final AppProperties properties;
    private final SecureRandom random = new SecureRandom();
    private final byte[] hmacKey;

    @Value("${app.mail.log-verification-codes:false}")
    private boolean logVerificationCodes;

    public EmailCodeService(StringRedisTemplate redis, RedisRateLimiter rateLimiter,
                            EmailSender emailSender, MailSenderHolder mailSenderHolder,
                            AppProperties properties) {
        this.redis = redis;
        this.rateLimiter = rateLimiter;
        this.emailSender = emailSender;
        this.mailSenderHolder = mailSenderHolder;
        this.properties = properties;
        this.hmacKey = resolveKey(properties);
    }

    /** 发送前检查三层限流；超限直接抛出对应错误码。 */
    public void checkSendLimits(String email, String clientIp) {
        if (!rateLimiter.tryAcquire("auth:code:gap:" + email, 1, RESEND_GAP_SECONDS)) {
            throw new BusinessException(ErrorCode.EMAIL_SEND_TOO_FREQUENT);
        }
        String day = LocalDate.now(ZONE).format(DAY);
        if (!rateLimiter.tryAcquire("auth:code:daily:" + email + ":" + day, DAILY_LIMIT, 86_400)) {
            throw new BusinessException(ErrorCode.EMAIL_SEND_TOO_FREQUENT);
        }
        if (clientIp != null && !clientIp.isBlank()) {
            String hour = LocalDateTime.now(ZONE).format(HOUR);
            rateLimiter.check("auth:code:ip:" + clientIp + ":" + hour, IP_HOURLY_LIMIT, 3_600);
        }
    }

    /** 生成验证码、覆盖旧码并发送邮件；返回是否已交给发送通道。 */
    public boolean issue(String email, Scene scene) {
        String code = generateCode();
        String key = key(email, scene);
        redis.delete(key);
        redis.opsForHash().put(key, "digest", digest(email, scene, code));
        redis.opsForHash().put(key, "attempts", "0");
        redis.expire(key, java.time.Duration.ofSeconds(CODE_TTL_SECONDS));

        boolean sent = true;
        try {
            emailSender.send(email, subject(scene), body(scene, code));
        } catch (Exception ex) {
            log.error("verification email send failed scene={}", scene, ex);
            sent = false;
        }
        if (!mailSenderHolder.isSmtpEnabled() && logVerificationCodes) {
            // 仅本地开发显式开启；生产环境必须保持 false。
            log.info("[auth-dev] scene={} email={} code={} expiresIn={}s",
                    scene, email, code, CODE_TTL_SECONDS);
        }
        return sent;
    }

    /** 校验并消费验证码；失败或过期统一返回 1004。 */
    public void verify(String email, Scene scene, String code) {
        if (code == null || !code.matches("\\d{6}")) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        Long result = redis.execute(VERIFY_SCRIPT, List.of(key(email, scene)),
                digest(email, scene, code), String.valueOf(MAX_ATTEMPTS));
        if (result == null || result != 1L) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
    }

    private String key(String email, Scene scene) {
        return "auth:code:" + scene.name().toLowerCase() + ":" + email;
    }

    private String generateCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private String digest(String email, Scene scene, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            byte[] out = mac.doFinal((email + "|" + scene.name() + "|" + code)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC unavailable", ex);
        }
    }

    private String subject(Scene scene) {
        return scene == Scene.VERIFY ? "验证你的旅迹地图邮箱" : "重置你的旅迹地图密码";
    }

    private String body(Scene scene, String code) {
        String action = scene == Scene.VERIFY ? "验证邮箱" : "重置密码";
        return "你的旅迹地图" + action + "验证码：" + code
                + "，" + (CODE_TTL_SECONDS / 60) + " 分钟内有效。请勿向他人泄露。";
    }

    private static byte[] resolveKey(AppProperties properties) {
        String raw = properties.getCrypto().getAesKey();
        if (raw == null || raw.isBlank()) {
            return "travelmapper-email-code".getBytes(StandardCharsets.UTF_8);
        }
        try {
            byte[] bytes = java.util.Base64.getDecoder().decode(raw.trim());
            return bytes.length >= 16 ? bytes : raw.getBytes(StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return raw.getBytes(StandardCharsets.UTF_8);
        }
    }
}