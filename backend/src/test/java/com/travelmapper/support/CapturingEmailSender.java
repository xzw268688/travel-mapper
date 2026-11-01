package com.travelmapper.support;

import com.travelmapper.service.auth.EmailSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 测试用邮件通道：保留正文，便于读取验证码。 */
public class CapturingEmailSender implements EmailSender {

    private static final Pattern CODE = Pattern.compile("验证码：(\\d{6})");

    private final Map<String, List<String>> bodies = new ConcurrentHashMap<>();

    @Override
    public void send(String to, String subject, String body) {
        bodies.computeIfAbsent(to.toLowerCase(), key -> new ArrayList<>()).add(body);
    }

    /** 返回该邮箱最后一次收到的验证码；没有则返回 null。 */
    public String lastCode(String to) {
        List<String> list = bodies.get(to.toLowerCase());
        if (list == null || list.isEmpty()) {
            return null;
        }
        Matcher matcher = CODE.matcher(list.get(list.size() - 1));
        return matcher.find() ? matcher.group(1) : null;
    }

    public int count(String to) {
        List<String> list = bodies.get(to.toLowerCase());
        return list == null ? 0 : list.size();
    }

    public void clear() {
        bodies.clear();
    }
}