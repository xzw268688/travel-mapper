package com.travelmapper.service.search;

import java.util.List;

public interface SearchProvider {

    List<GuideLink> searchGuides(GuideSearchQuery query);

    String providerName();
}
