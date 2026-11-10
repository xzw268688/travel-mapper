package com.travelmapper.common.security;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

/**
 * RFC 6238 TOTP：HMAC-SHA1、30 秒步长、6 位码，校验允许前后各一个时间窗。
 * 密钥使用 RFC 4648 Base32 编码，20 字节随机数。
 */
@Service
public class TotpService {

    public static final int DIGITS = 6;
    public static final long STEP_SECONDS = 30;
    private static final int WINDOW = 1;
    private static final int SECRET_BYTES = 20;
    private static final int MODULO = 1_000_000;

    private final SecureRandom random = new SecureRandom();

    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return Base32.encode(bytes);
    }

    public String otpauthUri(String issuer, String account, String secret) {
        return "otpauth://totp/" + encode(issuer) + ":" + encode(account)
                + "?secret=" + secret + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    public boolean verify(String secret, String code) {
        return verify(secret, code, Instant.now());
    }

    public boolean verify(String secret, String code, Instant now) {
        if (secret == null || secret.isBlank() || code == null || !code.matches("\\d{6}")) {
            return false;
        }
        byte[] key;
        try {
            key = Base32.decode(secret);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        long counter = now.getEpochSecond() / STEP_SECONDS;
        byte[] provided = code.getBytes(StandardCharsets.US_ASCII);
        for (int offset = -WINDOW; offset <= WINDOW; offset++) {
            byte[] expected = codeAt(counter + offset, key).getBytes(StandardCharsets.US_ASCII);
            if (MessageDigest.isEqual(expected, provided)) {
                return true;
            }
        }
        return false;
    }

    /** 便于测试与排查：返回指定时间戳对应的 6 位码。 */
    public String codeAt(String secret, long epochSeconds) {
        byte[] key = Base32.decode(secret);
        return codeAt(epochSeconds / STEP_SECONDS, key);
    }

    private String codeAt(long counter, byte[] key) {
        byte[] hash = hmacSha1(key, counter);
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        return String.format(Locale.ROOT, "%0" + DIGITS + "d", binary % MODULO);
    }

    private byte[] hmacSha1(byte[] key, long counter) {
        byte[] data = new byte[8];
        for (int i = 7; i >= 0; i--) {
            data[i] = (byte) (counter & 0xFF);
            counter >>>= 8;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA1 unavailable", ex);
        }
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}