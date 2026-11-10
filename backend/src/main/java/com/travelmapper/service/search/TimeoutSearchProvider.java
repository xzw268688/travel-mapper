package com.travelmapper.service.search;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Enforces the read timeout at the abstraction boundary. Concrete providers own
 * their HTTP connect timeout (default 3s) and read timeout (default 5s).
 */
public class TimeoutSearchProvider implements SearchProvider, AutoCloseable {

    private final SearchProvider delegate;
    private final int readTimeoutSeconds;
    private final ExecutorService executor;

    public TimeoutSearchProvider(SearchProvider delegate, int readTimeoutSeconds) {
        this.delegate = delegate;
        this.readTimeoutSeconds = readTimeoutSeconds;
        this.executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "search-timeout");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public List<GuideLink> searchGuides(GuideSearchQuery query) {
        Future<List<GuideLink>> future = executor.submit(() -> delegate.searchGuides(query));
        try {
            return future.get(readTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new SearchTimeoutException("search timed out after " + readTimeoutSeconds + "s", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SearchException("search interrupted", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof SearchTimeoutException timeout) {
                throw timeout;
            }
            throw new SearchException("search provider failed", cause);
        }
    }

    @Override
    public String providerName() {
        return delegate.providerName();
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
