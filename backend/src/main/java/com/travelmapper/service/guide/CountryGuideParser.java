package com.travelmapper.service.guide;

import com.travelmapper.controller.guide.dto.CountryGuideDtos;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把模型输出的固定 Markdown 章节解析为结构化攻略。
 * 章节缺失或字段缺失时降级为空值，不抛异常；调用方负责判断结果是否全空。
 */
@Component
public class CountryGuideParser {

    private static final Pattern INTRO = section("国家介绍");
    private static final Pattern ATTRACTIONS = section("推荐景点");
    private static final Pattern NOTICE = section("旅游须知");
    private static final int MAX_NOTES = 5;

    public Result parse(String raw) {
        String text = stripCodeFence(raw == null ? "" : raw.trim());
        Matcher introMatcher = INTRO.matcher(text);
        Matcher attractionsMatcher = ATTRACTIONS.matcher(text);
        Matcher noticeMatcher = NOTICE.matcher(text);
        boolean hasSections = introMatcher.find() || attractionsMatcher.find() || noticeMatcher.find();
        if (!hasSections) {
            return new Result(text, List.of(), emptyNotice());
        }
        String intro = introMatcher.reset().find() ? introMatcher.group(1).trim() : "";
        List<CountryGuideDtos.Attraction> attractions = attractionsMatcher.reset().find()
                ? parseAttractions(attractionsMatcher.group(1))
                : List.of();
        CountryGuideDtos.Notice notice = noticeMatcher.reset().find()
                ? parseNotice(noticeMatcher.group(1))
                : emptyNotice();
        return new Result(intro, attractions, notice);
    }

    private List<CountryGuideDtos.Attraction> parseAttractions(String block) {
        List<CountryGuideDtos.Attraction> result = new ArrayList<>();
        for (String line : block.split("\\R")) {
            String item = stripBullet(line);
            if (item == null) {
                continue;
            }
            String[] pair = splitLabel(item);
            String name = pair[0].trim();
            if (name.isEmpty()) {
                continue;
            }
            result.add(new CountryGuideDtos.Attraction(name, pair[1].trim()));
        }
        return result;
    }

    private CountryGuideDtos.Notice parseNotice(String block) {
        String visa = "";
        String currency = "";
        String powerPlug = "";
        String bestSeason = "";
        List<String> notes = new ArrayList<>();
        for (String line : block.split("\\R")) {
            String item = stripBullet(line);
            if (item == null) {
                continue;
            }
            String[] pair = splitLabel(item);
            String label = pair[0].trim().toLowerCase(Locale.ROOT);
            String value = pair[1].trim();
            switch (label) {
                case "签证" -> visa = value;
                case "货币" -> currency = value;
                case "插头", "插座", "电源" -> powerPlug = value;
                case "最佳季节", "最佳时间", "季节", "天气" -> bestSeason = value;
                default -> {
                    if (notes.size() < MAX_NOTES) {
                        notes.add(value.isEmpty() ? item : value);
                    }
                }
            }
        }
        return new CountryGuideDtos.Notice(visa, currency, powerPlug, bestSeason, notes);
    }

    /** 返回去掉项目符号的内容；空行或非列表行返回 null（攻略章节只接受列表项）。 */
    private String stripBullet(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("•")) {
            return trimmed.substring(trimmed.startsWith("•") ? 1 : 2).trim();
        }
        return trimmed;
    }

    /** 按第一个中文或英文冒号拆分为“标签 / 内容”。 */
    private String[] splitLabel(String item) {
        int index = indexOfColon(item);
        if (index < 0) {
            return new String[]{item, ""};
        }
        return new String[]{item.substring(0, index).trim(), item.substring(index + 1).trim()};
    }

    private int indexOfColon(String text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '：' || ch == ':') {
                return i;
            }
        }
        return -1;
    }

    private CountryGuideDtos.Notice emptyNotice() {
        return new CountryGuideDtos.Notice("", "", "", "", List.of());
    }

    private static Pattern section(String title) {
        return Pattern.compile("(?ms)^#{1,3}\\s*" + Pattern.quote(title) + "\\s*$(.*?)(?=^#{1,3}\\s*|\\z)");
    }

    private String stripCodeFence(String text) {
        if (!text.startsWith("```")) {
            return text;
        }
        int firstNewline = text.indexOf('\n');
        String body = firstNewline >= 0 ? text.substring(firstNewline + 1) : text;
        return body.endsWith("```") ? body.substring(0, body.length() - 3).trim() : body.trim();
    }

    public record Result(String intro, List<CountryGuideDtos.Attraction> attractions,
                         CountryGuideDtos.Notice notice) {

        public boolean isEmpty() {
            return intro.isBlank()
                    && attractions.isEmpty()
                    && notice.visa().isBlank()
                    && notice.currency().isBlank()
                    && notice.powerPlug().isBlank()
                    && notice.bestSeason().isBlank()
                    && notice.notes().isEmpty();
        }
    }
}