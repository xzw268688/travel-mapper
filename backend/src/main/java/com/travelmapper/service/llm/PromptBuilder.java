package com.travelmapper.service.llm;

import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    public String buildItineraryPrompt(LlmItineraryRequest request) {
        String cities = String.join("、", zip(request.cityCodes(), request.cityNames()));
        return """
                你是 TravelMapper 的行程规划助手。只输出一个 JSON 对象，不要 Markdown、不要解释。
                国家：%s（%s）
                可选城市：%s
                天数：%d
                开始日期：%s
                偏好：%s

                JSON Schema：
                {"countryCode":"%s","days":[{"dayIndex":1,"cityCode":"%s","spots":[{"name":"景点名","description":"简述","suggestedDuration":"2小时"}]}],"preparations":{"visa":"签证提示","currency":"货币提示","powerPlug":"插头提示","bestSeason":"最佳季节","notes":["提醒1","提醒2"]}}

                要求：
                1. days 数组长度必须等于 %d，dayIndex 从 1 连续递增。
                2. 每天 cityCode 必须来自可选城市集合；多城市时按天分配，尽量连续。
                3. 每天 1-6 个景点，name 必填；description 和 suggestedDuration 可为空字符串。
                4. preparations 必须包含 visa、currency、powerPlug、bestSeason、notes 五项；notes 为字符串数组，最多 5 条。
                5. 不要输出 guides；攻略链接由后端搜索填充。
                """.formatted(
                request.countryName(), request.countryCode(),
                cities.isBlank() ? request.cityCodes().get(0) : cities,
                request.days(), request.startDate(),
                request.preference() == null ? "" : request.preference(),
                request.countryCode(),
                request.cityCodes().get(0),
                request.days());
    }

    public String buildPolishPrompt(String text, String style, String target) {
        String styleText = switch (style == null ? "vivid" : style) {
            case "concise" -> "简洁凝练";
            case "elegant" -> "优雅文艺";
            default -> "生动自然";
        };
        String targetText = "full".equals(target) ? "润色全文" : "润色选中文本";
        return "你是中文旅行游记编辑。请用" + styleText + "的风格" + targetText
                + "。只输出润色后的文本，不要解释、不要 Markdown。\n\n" + text;
    }

    private String[] zip(java.util.List<String> codes, java.util.List<String> names) {
        String[] result = new String[codes.size()];
        for (int i = 0; i < codes.size(); i++) {
            String name = names != null && i < names.size() ? names.get(i) : codes.get(i);
            result[i] = name + "（" + codes.get(i) + "）";
        }
        return result;
    }
}
