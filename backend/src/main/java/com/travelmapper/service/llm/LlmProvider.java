package com.travelmapper.service.llm;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public interface LlmProvider {

    String providerName();

    String generateItinerary(LlmItineraryRequest request);

    void polishStream(String text, String style, String target,
                      Consumer<String> onDelta, BooleanSupplier cancelled);
}
