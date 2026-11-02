package com.travelmapper.service.guide;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CountryGuideParserTest {

    private final CountryGuideParser parser = new CountryGuideParser();

    @Test
    void parsesAllThreeSections() {
        CountryGuideParser.Result result = parser.parse("""
                ## 国家介绍
                日本适合城市漫游与美食旅行。

                ## 推荐景点
                - 富士山：日本象征
                - 清水寺：古都名胜

                ## 旅游须知
                - 签证：需提前办理
                - 货币：日元
                - 插头：A 型
                - 最佳季节：春秋
                - 提示：地铁拥挤
                - 提示：餐厅需预约
                """);
        assertThat(result.isEmpty()).isFalse();
        assertThat(result.intro()).isEqualTo("日本适合城市漫游与美食旅行。");
        assertThat(result.attractions()).hasSize(2);
        assertThat(result.attractions().get(0).name()).isEqualTo("富士山");
        assertThat(result.attractions().get(0).description()).isEqualTo("日本象征");
        assertThat(result.notice().visa()).isEqualTo("需提前办理");
        assertThat(result.notice().currency()).isEqualTo("日元");
        assertThat(result.notice().powerPlug()).isEqualTo("A 型");
        assertThat(result.notice().bestSeason()).isEqualTo("春秋");
        assertThat(result.notice().notes()).containsExactly("地铁拥挤", "餐厅需预约");
    }

    @Test
    void missingSectionsDegradeToEmptyValues() {
        CountryGuideParser.Result result = parser.parse("""
                ## 国家介绍
                只有介绍。

                ## 推荐景点
                - 富士山：日本象征
                """);
        assertThat(result.intro()).isEqualTo("只有介绍。");
        assertThat(result.attractions()).hasSize(1);
        assertThat(result.notice().visa()).isEmpty();
        assertThat(result.notice().notes()).isEmpty();
        assertThat(result.isEmpty()).isFalse();
    }

    @Test
    void blankOutputIsEmpty() {
        CountryGuideParser.Result result = parser.parse("   ");
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void outputWithoutHeadingsFallsBackToIntro() {
        CountryGuideParser.Result result = parser.parse("日本是一个适合旅行的国家。");
        assertThat(result.intro()).isEqualTo("日本是一个适合旅行的国家。");
        assertThat(result.isEmpty()).isFalse();
    }

    @Test
    void codeFenceIsStripped() {
        CountryGuideParser.Result result = parser.parse("""
                ```markdown
                ## 国家介绍
                围栏内的介绍。
                ```
                """);
        assertThat(result.intro()).isEqualTo("围栏内的介绍。");
    }
}