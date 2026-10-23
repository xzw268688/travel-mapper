package com.travelmapper.service.search;

import java.util.List;

public class NoopSearchProvider implements SearchProvider {

    @Override
    public List<GuideLink> searchGuides(GuideSearchQuery query) {
        return List.of();
    }

    @Override
    public String providerName() {
        return "none";
    }
}
