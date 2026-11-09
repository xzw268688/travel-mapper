package com.travelmapper.common.log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SensitiveDataMasker {

    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)(\"?(?:password|passwd|token|access[_-]?key|secret[_-]?key|api[_-]?key|authorization|secret)\"?"
                    + "\\s*[:=]\\s*)(\"[^\"]*\"|'[^']*'|[^\\s,;}\\]]+)");
    private static final Pattern BEARER = Pattern.compile("(?i)(bearer)\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
    private static final Pattern JSON_NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?");

    private SensitiveDataMasker() {
    }

    public static String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        Matcher matcher = KEY_VALUE.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String value = matcher.group(2);
            boolean quoted = value.startsWith("\"") || value.startsWith("'");
            // JSON 中的 null / 布尔 / 数字没有引号；直接替换成裸 **** 会让 detail 变成非法 JSON。
            boolean jsonLiteral = !quoted && ("null".equals(value) || "true".equals(value)
                    || "false".equals(value) || JSON_NUMBER.matcher(value).matches());
            String replacement = matcher.group(1) + (quoted || jsonLiteral ? "\"****\"" : "****");
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        String masked = buffer.toString();
        masked = BEARER.matcher(masked).replaceAll("$1 ****");
        return JWT.matcher(masked).replaceAll("****");
    }
}
