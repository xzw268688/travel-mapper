package com.travelmapper.service.expense;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.time.TimeProvider;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.controller.admin.dto.ExchangeRateDtos;
import com.travelmapper.repository.entity.ExchangeRate;
import com.travelmapper.repository.mapper.ExchangeRateMapper;
import com.travelmapper.service.audit.AuditLogService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ExchangeRateService {

    private final ExchangeRateMapper mapper;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;
    private final ExchangeRateProvider provider;
    private final TimeProvider timeProvider;

    public ExchangeRateService(ExchangeRateMapper mapper, JdbcTemplate jdbcTemplate,
                               AuditLogService auditLogService, ExchangeRateProvider provider,
                               TimeProvider timeProvider) {
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogService = auditLogService;
        this.provider = provider;
        this.timeProvider = timeProvider;
    }

    public List<ExchangeRateDtos.RateView> list() {
        return mapper.selectList(new LambdaQueryWrapper<ExchangeRate>()
                        .orderByAsc(ExchangeRate::getCurrency))
                .stream()
                .map(rate -> new ExchangeRateDtos.RateView(
                        rate.getId(), rate.getCurrency(),
                        rate.getRateToCny().setScale(6, RoundingMode.HALF_UP).toPlainString(),
                        rate.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public ExchangeRateDtos.RefreshResponse refresh(boolean apply) {
        List<ExchangeRate> existing = mapper.selectList(new LambdaQueryWrapper<ExchangeRate>()
                .orderByAsc(ExchangeRate::getCurrency));
        if (existing.isEmpty()) {
            return new ExchangeRateDtos.RefreshResponse(provider.source(), timeProvider.now(),
                    apply, 0, 0, 0, List.of());
        }
        Set<String> currencies = existing.stream()
                .map(ExchangeRate::getCurrency)
                .collect(Collectors.toSet());
        Map<String, BigDecimal> latest;
        try {
            latest = provider.latestToCny(currencies);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.RATE_FEED_UNAVAILABLE);
        }

        List<ExchangeRateDtos.RefreshItem> items = new ArrayList<>();
        Map<String, Map<String, String>> changes = new LinkedHashMap<>();
        int updated = 0;
        int unchanged = 0;
        int unsupported = 0;
        for (ExchangeRate rate : existing) {
            String currency = rate.getCurrency();
            String oldText = rate.getRateToCny().setScale(6, RoundingMode.HALF_UP).toPlainString();
            BigDecimal market = latest.get(currency);
            if (market == null) {
                items.add(new ExchangeRateDtos.RefreshItem(currency, oldText, null, "UNSUPPORTED"));
                unsupported++;
                continue;
            }
            BigDecimal newRate = market.setScale(6, RoundingMode.HALF_UP);
            String newText = newRate.toPlainString();
            if (newRate.compareTo(rate.getRateToCny()) == 0) {
                items.add(new ExchangeRateDtos.RefreshItem(currency, oldText, newText, "UNCHANGED"));
                unchanged++;
                continue;
            }
            items.add(new ExchangeRateDtos.RefreshItem(currency, oldText, newText, "CHANGED"));
            updated++;
            if (apply) {
                rate.setRateToCny(newRate);
                rate.setUpdatedBy(CurrentUser.id());
                mapper.updateById(rate);
                changes.put(currency, Map.of("old", oldText, "new", newText));
            }
        }
        if (apply && updated > 0) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("source", provider.source());
            detail.put("updated", updated);
            detail.put("changes", changes);
            auditLogService.record(CurrentUser.id(), "RATE_REFRESH", "RATE", null, detail, null);
        }
        return new ExchangeRateDtos.RefreshResponse(provider.source(), timeProvider.now(),
                apply, updated, unchanged, unsupported, items);
    }

    @Transactional
    public ExchangeRateDtos.UpdateResponse upsert(ExchangeRateDtos.UpdateRequest request) {
        String currency = request.getCurrency().trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "币种必须为 3 位大写字母");
        }
        BigDecimal rate;
        try {
            rate = new BigDecimal(request.getRateToCny());
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "汇率格式非法");
        }
        if (rate.signum() <= 0 || rate.scale() > 6) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "汇率必须大于 0 且最多 6 位小数");
        }
        rate = rate.setScale(6, RoundingMode.HALF_UP);
        ExchangeRate existing = mapper.selectOne(new LambdaQueryWrapper<ExchangeRate>()
                .eq(ExchangeRate::getCurrency, currency));
        String rateText = rate.toPlainString();
        if (existing != null) {
            String previous = existing.getRateToCny().setScale(6, RoundingMode.HALF_UP).toPlainString();
            existing.setRateToCny(rate);
            existing.setUpdatedBy(CurrentUser.id());
            mapper.updateById(existing);
            auditLogService.record(CurrentUser.id(), "RATE_UPDATE", "RATE", existing.getId(),
                    Map.of("currency", currency, "old", previous, "new", rateText), null);
            return new ExchangeRateDtos.UpdateResponse(currency, rateText, previous, false, 0);
        }
        ExchangeRate created = new ExchangeRate();
        created.setCurrency(currency);
        created.setRateToCny(rate);
        created.setUpdatedBy(CurrentUser.id());
        mapper.insert(created);
        int affected = jdbcTemplate.update(
                "UPDATE expense SET exchange_rate_snapshot = ? "
                        + "WHERE currency = ? AND exchange_rate_snapshot IS NULL AND deleted = 0",
                rate, currency);
        auditLogService.record(CurrentUser.id(), "RATE_CREATE", "RATE", created.getId(),
                Map.of("currency", currency, "rate", rateText, "affected", affected), null);
        return new ExchangeRateDtos.UpdateResponse(currency, rateText, null, true, affected);
    }
}
