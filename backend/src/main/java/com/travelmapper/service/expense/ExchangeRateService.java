package com.travelmapper.service.expense;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travelmapper.common.api.BusinessException;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ExchangeRateService {

    private final ExchangeRateMapper mapper;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    public ExchangeRateService(ExchangeRateMapper mapper, JdbcTemplate jdbcTemplate,
                               AuditLogService auditLogService) {
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogService = auditLogService;
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
