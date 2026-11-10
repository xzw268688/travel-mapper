package com.travelmapper.service.config;

import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ConfigReloadService {

    private final List<ReloadableProvider> providers;

    public ConfigReloadService(List<ReloadableProvider> providers) {
        this.providers = providers;
    }

    /**
     * Rebuild every provider from current sys_config. A failing provider keeps its
     * previous instance; the failure is surfaced so the caller can reject the update.
     */
    public void reloadAll() {
        RuntimeException failure = null;
        for (ReloadableProvider provider : providers) {
            try {
                provider.reload();
            } catch (RuntimeException ex) {
                log.error("provider reload failed: {}", provider.getClass().getSimpleName(), ex);
                if (failure == null) {
                    failure = ex;
                }
            }
        }
        if (failure != null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "配置无法生效：" + failure.getMessage());
        }
    }

    public void reloadAllQuietly() {
        for (ReloadableProvider provider : providers) {
            try {
                provider.reload();
            } catch (RuntimeException ex) {
                log.warn("quiet provider reload failed: {}", provider.getClass().getSimpleName(), ex);
            }
        }
    }
}
