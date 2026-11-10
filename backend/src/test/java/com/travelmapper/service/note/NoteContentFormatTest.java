package com.travelmapper.service.note;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoteContentFormatTest {

    @Test
    void relativeObjectImagePathsSurviveRelaxedSafelist() {
        String html = "<p>正文</p>"
                + "<img src=\"/objects/note/1/2026/10/abc.jpg\" alt=\"游记图片\">"
                + "<script>alert(1)</script>";

        String clean = Jsoup.clean(
                html, "https://travelmapper.invalid/", Safelist.relaxed().preserveRelativeLinks(true));

        assertThat(clean).contains("/objects/note/1/2026/10/abc.jpg");
        assertThat(clean).doesNotContain("<script");
    }

    @Test
    void preserveRelativeLinksDoesNotAllowDangerousProtocols() {
        String clean = Jsoup.clean(
                "<a href=\"javascript:alert(1)\">x</a><img src=\"https://example.com/a.png\">",
                "https://travelmapper.invalid/",
                Safelist.relaxed().preserveRelativeLinks(true));

        assertThat(clean).doesNotContain("javascript:");
        assertThat(clean).contains("https://example.com/a.png");
    }

    @Test
    void tipTapFormattingSurvivesRelaxedSafelist() {
        String html = "<h2>小标题</h2><p><strong>加粗</strong> <em>斜体</em></p>"
                + "<ul><li>列表</li></ul><blockquote><p>引用</p></blockquote>"
                + "<p><a href=\"https://example.com\">链接</a></p>";

        String clean = Jsoup.clean(html, Safelist.relaxed());

        assertThat(clean).contains("<h2>", "<strong>", "<em>", "<ul>", "<blockquote>",
                "href=\"https://example.com\"");
    }
}
