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
                {"countryCode":"%s","days":[{"dayIndex":1,"cityCode":"%s","spots":[{"name":"景点名","description":"简述","suggestedDuration":"2小时","category":"类别","ticketPrice":"门票","highlights":"亮点","bestTime":"最佳时段","nearestStation":"最近车站","reservationRequired":false,"area":"区域","warningNotes":"避雷备注"}]}]}

                要求：
                1. days 数组长度必须等于 %d，dayIndex 从 1 连续递增。
                2. 每天 cityCode 必须来自可选城市集合；多城市时按天分配，尽量连续。
                3. 每天 1-6 个景点，name 必填；description 和 suggestedDuration 可为空字符串。
                4. 每个景点必须输出 category（类别）、ticketPrice（门票）、highlights（亮点）、bestTime（最佳时段）、nearestStation（最近车站）、reservationRequired（是否需预约，布尔值）、area（区域）、warningNotes（避雷备注）。
                5. ticketPrice 使用目的地原币文本（如“约 2000 円”“免费”），不要换算成人民币；不确定的字段输出空字符串，不要编造。
                6. 不要输出 guides、preparations 或其他额外字段；攻略链接与行前须知由其他接口提供。
                """.formatted(
                request.countryName(), request.countryCode(),
                cities.isBlank() ? request.cityCodes().get(0) : cities,
                request.days(), request.startDate(),
                request.preference() == null ? "" : request.preference(),
                request.countryCode(),
                request.cityCodes().get(0),
                request.days());
    }

    public String buildCountryGuidePrompt(String countryName, String countryCode) {
        return """
                你是 TravelMapper 的国家旅行攻略编辑。请用简体中文介绍 %s（%s），并严格按以下 Markdown 结构输出。
                不要输出任何额外解释、不要使用代码块、不要改变章节标题。

                ## 国家介绍
                （150-250 字，说明旅行特色、文化氛围与适合人群）

                ## 推荐景点
                - 景点名：一句话推荐理由
                - 景点名：一句话推荐理由
                （共 4-6 条，优先该国最具代表性的地点）

                ## 旅游须知
                - 签证：签证与入境提示
                - 货币：货币与支付提示
                - 插头：电源插头与电压提示
                - 最佳季节：最佳旅行月份与天气提示
                - 提示：一条实用提醒（可重复 2-3 条“提示”）

                要求：三个章节必须齐全；推荐景点使用“名称：描述”格式；旅游须知只使用上述标签。
                """.formatted(countryName, countryCode);
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
