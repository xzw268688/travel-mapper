package com.travelmapper.service.card;

import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;

@Component
public class CardCodeGenerator {

    /** Excludes 0/O, 1/I/L to reduce transcription errors. */
    private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    private final SecureRandom random = new SecureRandom();

    public String generate(String rawPrefix) {
        String prefix = normalizePrefix(rawPrefix);
        StringBuilder builder = new StringBuilder(prefix).append('-');
        for (int group = 0; group < 4; group++) {
            if (group > 0) {
                builder.append('-');
            }
            for (int i = 0; i < 4; i++) {
                builder.append(ALPHABET[random.nextInt(ALPHABET.length)]);
            }
        }
        return builder.toString();
    }

    public String normalizePrefix(String rawPrefix) {
        if (rawPrefix == null || rawPrefix.isBlank()) {
            return "TM";
        }
        String prefix = rawPrefix.trim().toUpperCase(Locale.ROOT);
        if (!prefix.matches("[A-Z]{2,6}")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "卡密前缀必须为 2-6 位大写字母");
        }
        return prefix;
    }
}
