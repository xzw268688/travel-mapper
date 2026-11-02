package com.travelmapper.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.service.llm.LlmItineraryRequest;
import com.travelmapper.service.llm.LlmProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class FakeLlmProvider implements LlmProvider {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger prepCounter = new AtomicInteger();
    private final AtomicInteger guideCalls = new AtomicInteger();
    private volatile boolean failFirst;
    private volatile boolean alwaysFail;
    private volatile boolean invalidMultiCity;
    private volatile boolean failPolish;
    private volatile boolean failGuide;
    private volatile boolean failGuideAfterOutput;
    private volatile String guideOutput = DEFAULT_GUIDE;

    public static final String DEFAULT_GUIDE = """
            ## 国家介绍
            日本是位于东亚的岛国，四季分明，适合城市漫游与美食旅行。

            ## 推荐景点
            - 富士山：日本象征，适合远眺与登山
            - 清水寺：古都文化与红叶名胜
            - 浅草寺：感受下町风情

            ## 旅游须知
            - 签证：需提前办理签证
            - 货币：日元，现金仍较常用
            - 插头：A 型插头，100V
            - 最佳季节：春秋两季
            - 提示：地铁高峰较为拥挤
            """;

    @Override
    public String providerName() {
        return "fake";
    }

    @Override
    public String generateItinerary(LlmItineraryRequest request) {
        int call = calls.incrementAndGet();
        if (alwaysFail || (failFirst && call == 1)) {
            return "this is not json";
        }
        boolean invalid = invalidMultiCity && request.cityCodes().size() > 1;
        String city = invalid ? "XXX" : request.cityCodes().get(0);
        List<Map<String, Object>> days = new ArrayList<>();
        for (int i = 0; i < request.days(); i++) {
            String dayCity = invalid ? "XXX"
                    : request.cityCodes().get(i % request.cityCodes().size());
            Map<String, Object> spot = new LinkedHashMap<>();
            spot.put("name", "景点" + (i + 1));
            spot.put("description", "测试景点");
            spot.put("suggestedDuration", "2小时");
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("dayIndex", i + 1);
            day.put("cityCode", dayCity);
            day.put("spots", List.of(spot));
            days.add(day);
        }
        int prep = prepCounter.incrementAndGet();
        Map<String, Object> preparations = new LinkedHashMap<>();
        preparations.put("visa", "visa-" + prep);
        preparations.put("currency", "currency-" + prep);
        preparations.put("powerPlug", "plug-" + prep);
        preparations.put("bestSeason", "season-" + prep);
        preparations.put("notes", List.of("note-" + prep));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("countryCode", request.countryCode());
        root.put("days", days);
        root.put("preparations", preparations);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public void polishStream(String text, String style, String target,
                             Consumer<String> onDelta, BooleanSupplier cancelled) {
        if (failPolish) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
        for (int i = 0; i < text.length(); i += 2) {
            if (cancelled.getAsBoolean()) {
                return;
            }
            onDelta.accept(text.substring(i, Math.min(text.length(), i + 2)));
        }
    }

    @Override
    public void guideStream(String countryName, String countryCode,
                            Consumer<String> onDelta, BooleanSupplier cancelled) {
        guideCalls.incrementAndGet();
        if (failGuide) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
        String output = guideOutput;
        for (int i = 0; i < output.length(); i += 6) {
            if (cancelled.getAsBoolean()) {
                return;
            }
            onDelta.accept(output.substring(i, Math.min(output.length(), i + 6)));
        }
        if (failGuideAfterOutput) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
    }

    public void reset() {
        calls.set(0);
        prepCounter.set(0);
        guideCalls.set(0);
        failFirst = false;
        alwaysFail = false;
        invalidMultiCity = false;
        failPolish = false;
        failGuide = false;
        failGuideAfterOutput = false;
        guideOutput = DEFAULT_GUIDE;
    }

    public void setFailFirst(boolean failFirst) {
        this.failFirst = failFirst;
    }

    public void setAlwaysFail(boolean alwaysFail) {
        this.alwaysFail = alwaysFail;
    }

    public void setInvalidMultiCity(boolean invalidMultiCity) {
        this.invalidMultiCity = invalidMultiCity;
    }

    public void setFailPolish(boolean failPolish) {
        this.failPolish = failPolish;
    }

    public void setFailGuide(boolean failGuide) {
        this.failGuide = failGuide;
    }

    public void setFailGuideAfterOutput(boolean failGuideAfterOutput) {
        this.failGuideAfterOutput = failGuideAfterOutput;
    }

    public void setGuideOutput(String guideOutput) {
        this.guideOutput = guideOutput == null ? "" : guideOutput;
    }

    public int calls() {
        return calls.get();
    }

    public int prepCounter() {
        return prepCounter.get();
    }

    public int guideCalls() {
        return guideCalls.get();
    }
}
