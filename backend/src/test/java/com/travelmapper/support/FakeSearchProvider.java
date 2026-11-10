package com.travelmapper.support;

import com.travelmapper.service.search.GuideLink;
import com.travelmapper.service.search.GuideSearchQuery;
import com.travelmapper.service.search.SearchProvider;
import com.travelmapper.service.search.SearchTimeoutException;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeSearchProvider implements SearchProvider {

    private final AtomicInteger calls = new AtomicInteger();
    private volatile boolean slow;

    @Override
    public List<GuideLink> searchGuides(GuideSearchQuery query) {
        calls.incrementAndGet();
        if (slow) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new SearchTimeoutException("interrupted");
            }
        }
        return List.of(
                new GuideLink("攻略一", "https://example.com/one", "example.com"),
                new GuideLink("攻略二", "https://example.com/two", "example.com"),
                new GuideLink("攻略三", "https://example.com/three", "example.com"));
    }

    @Override
    public String providerName() {
        return "fake";
    }

    public void reset() {
        calls.set(0);
        slow = false;
    }

    public void setSlow(boolean slow) {
        this.slow = slow;
    }

    public int calls() {
        return calls.get();
    }
}
