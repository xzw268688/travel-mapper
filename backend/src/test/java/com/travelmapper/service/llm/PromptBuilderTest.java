package com.travelmapper.service.llm;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {

    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    void itineraryPromptCarriesInputsAndJsonContract() {
        String prompt = promptBuilder.buildItineraryPrompt(new LlmItineraryRequest(
                "JP", "日本", List.of("TYO", "OSA"), List.of("东京", "大阪"),
                5, LocalDate.of(2025, 6, 1), "喜欢美食"));
        assertThat(prompt).contains("JSON Schema", "JP", "日本", "东京（TYO）", "大阪（OSA）",
                "5", "2025-06-01", "喜欢美食", "dayIndex");
        assertThat(prompt).contains("不要 Markdown");
        assertThat(prompt).doesNotContain("\"preparations\"");
    }

    @Test
    void countryGuidePromptCarriesCountryAndFixedSections() {
        String prompt = promptBuilder.buildCountryGuidePrompt("日本", "JP");
        assertThat(prompt).contains("日本", "JP", "## 国家介绍", "## 推荐景点", "## 旅游须知",
                "签证：", "货币：", "插头：", "最佳季节：", "提示：");
        assertThat(prompt).contains("简体中文");
    }

    @Test
    void polishPromptCarriesStyleAndText() {
        String prompt = promptBuilder.buildPolishPrompt("原文", "concise", "full");
        assertThat(prompt).contains("简洁凝练", "润色全文", "原文");
    }
}
