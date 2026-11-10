package com.travelmapper.service.card;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.common.api.PageRequest;
import com.travelmapper.common.api.PageResult;
import com.travelmapper.common.redis.DistributedLock;
import com.travelmapper.common.time.TimeProvider;
import com.travelmapper.controller.admin.dto.CardBatchStatsView;
import com.travelmapper.controller.admin.dto.CardKeyView;
import com.travelmapper.controller.admin.dto.CardRedemptionView;
import com.travelmapper.controller.admin.dto.GenerateCardsRequest;
import com.travelmapper.controller.admin.dto.GenerateCardsResponse;
import com.travelmapper.controller.admin.dto.VoidCardResponse;
import com.travelmapper.controller.card.dto.CardRedeemResponse;
import com.travelmapper.repository.entity.CardKey;
import com.travelmapper.repository.entity.User;
import com.travelmapper.repository.enums.CardStatus;
import com.travelmapper.repository.enums.CardType;
import com.travelmapper.repository.enums.QuotaSource;
import com.travelmapper.repository.mapper.CardKeyMapper;
import com.travelmapper.repository.mapper.UserMapper;
import com.travelmapper.service.audit.AuditLogService;
import com.travelmapper.service.quota.QuotaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class CardKeyService {

    private static final DateTimeFormatter BATCH_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CardKeyMapper cardKeyMapper;
    private final UserMapper userMapper;
    private final QuotaService quotaService;
    private final CardCodeGenerator codeGenerator;
    private final DistributedLock distributedLock;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;
    private final JdbcTemplate jdbcTemplate;
    private final TimeProvider timeProvider;
    private final SecureRandom random = new SecureRandom();

    public CardKeyService(CardKeyMapper cardKeyMapper, UserMapper userMapper, QuotaService quotaService,
                          CardCodeGenerator codeGenerator, DistributedLock distributedLock,
                          StringRedisTemplate redis, ObjectMapper objectMapper,
                          AuditLogService auditLogService, JdbcTemplate jdbcTemplate,
                          TimeProvider timeProvider) {
        this.cardKeyMapper = cardKeyMapper;
        this.userMapper = userMapper;
        this.quotaService = quotaService;
        this.codeGenerator = codeGenerator;
        this.distributedLock = distributedLock;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
        this.jdbcTemplate = jdbcTemplate;
        this.timeProvider = timeProvider;
    }

    @Transactional
    public GenerateCardsResponse generate(Long adminId, GenerateCardsRequest request) {
        CardType type = request.getType();
        if (type == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "卡密类型必填");
        }
        Integer value = request.getValue();
        if (value == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "面额/次数必填");
        }
        if (type == CardType.AI_QUOTA && (value < 1 || value > 100_000)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "AI 次数包面额必须在 1 到 100000 之间");
        }
        if (type == CardType.VIP && (value < 1 || value > 3650)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "VIP 天数必须在 1 到 3650 之间");
        }
        Integer quantity = request.getQuantity();
        if (quantity == null || quantity < 1 || quantity > 500) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "生成数量必须在 1 到 500 之间");
        }
        if (request.getExpireAt() != null && request.getExpireAt().isBefore(timeProvider.now())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "卡密有效期不能早于当前时间");
        }
        BigDecimal priceCny = request.getPriceCny();
        if (priceCny != null) {
            if (priceCny.signum() < 0) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "售价不能为负数");
            }
            if (priceCny.scale() > 2) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "售价最多保留两位小数");
            }
            priceCny = priceCny.setScale(2, RoundingMode.HALF_UP);
        }
        String prefix = codeGenerator.normalizePrefix(request.getPrefix());
        String batchId = "B" + timeProvider.now().format(BATCH_FORMAT)
                + String.format("%04d", random.nextInt(10_000));

        List<String> codes = new ArrayList<>(quantity);
        for (int i = 0; i < quantity; i++) {
            insertWithUniqueCode(batchId, prefix, type, value, priceCny, request.getExpireAt(), adminId, codes);
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("batchId", batchId);
        detail.put("type", type.name());
        detail.put("value", value);
        detail.put("quantity", quantity);
        if (priceCny != null) {
            detail.put("priceCny", priceCny.toPlainString());
        }
        auditLogService.record(adminId, "CARD_GENERATE", "CARD", null, detail, null);
        return new GenerateCardsResponse(batchId, quantity, codes);
    }

    public PageResult<CardKeyView> list(String batchId, CardStatus status, PageRequest request) {
        request.validate();
        LambdaQueryWrapper<CardKey> wrapper = new LambdaQueryWrapper<CardKey>()
                .eq(batchId != null && !batchId.isBlank(), CardKey::getBatchId, batchId)
                .eq(status != null, CardKey::getStatus, status)
                .orderByDesc(CardKey::getId);
        Page<CardKey> page = cardKeyMapper.selectPage(new Page<>(request.getPage(), request.getSize()), wrapper);
        List<CardKeyView> list = page.getRecords().stream().map(this::toView).toList();
        return PageResult.of(page.getTotal(), request, list);
    }

    /** 批次统计：数量口径按状态聚合，收入为「已用 × 售价」的估算值。 */
    public List<CardBatchStatsView> batchStats() {
        return jdbcTemplate.query(
                "SELECT batch_id, ANY_VALUE(type) AS type, ANY_VALUE(value) AS value, "
                        + "ANY_VALUE(price_cny) AS price_cny, MIN(created_at) AS created_at, "
                        + "COUNT(*) AS total, "
                        + "SUM(status = 'USED') AS used, SUM(status = 'UNUSED') AS unused, "
                        + "SUM(status = 'VOID') AS void_count, SUM(status = 'EXPIRED') AS expired "
                        + "FROM card_key GROUP BY batch_id ORDER BY MIN(id) DESC",
                (rs, rowNum) -> {
                    long used = rs.getLong("used");
                    BigDecimal price = rs.getBigDecimal("price_cny");
                    BigDecimal revenue = price == null ? null
                            : price.multiply(BigDecimal.valueOf(used)).setScale(2, RoundingMode.HALF_UP);
                    LocalDateTime created = rs.getObject("created_at", LocalDateTime.class);
                    return new CardBatchStatsView(
                            rs.getString("batch_id"),
                            rs.getString("type"),
                            rs.getInt("value"),
                            price,
                            created,
                            rs.getLong("total"),
                            used,
                            rs.getLong("unused"),
                            rs.getLong("void_count"),
                            rs.getLong("expired"),
                            revenue);
                });
    }

    /** 兑换记录：仅已使用卡密；可按用户筛选。 */
    public PageResult<CardRedemptionView> redemptions(Long userId, PageRequest request) {
        request.validate();
        String where = " FROM card_key k LEFT JOIN `user` u ON u.id = k.used_by "
                + "WHERE k.status = 'USED'" + (userId == null ? "" : " AND k.used_by = ?");
        List<Object> params = new ArrayList<>();
        if (userId != null) {
            params.add(userId);
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + where, Long.class,
                params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(request.getSize());
        pageParams.add(request.offset());
        List<CardRedemptionView> rows = jdbcTemplate.query(
                "SELECT k.id, k.code, k.batch_id, k.type, k.value, k.price_cny, "
                        + "k.used_by, u.username, k.used_at" + where
                        + " ORDER BY k.used_at DESC, k.id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> {
                    LocalDateTime usedAt = rs.getObject("used_at", LocalDateTime.class);
                    return new CardRedemptionView(
                            rs.getLong("id"),
                            rs.getString("code"),
                            rs.getString("batch_id"),
                            rs.getString("type"),
                            rs.getInt("value"),
                            rs.getBigDecimal("price_cny"),
                            rs.getObject("used_by") == null ? null : rs.getLong("used_by"),
                            rs.getString("username"),
                            usedAt);
                },
                pageParams.toArray());
        return PageResult.of(total == null ? 0 : total, request, rows);
    }

    public byte[] exportCsv(String batchId) {
        if (batchId == null || batchId.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "batchId 必填");
        }
        List<CardKey> cards = cardKeyMapper.selectList(new LambdaQueryWrapper<CardKey>()
                .eq(CardKey::getBatchId, batchId)
                .orderByAsc(CardKey::getId));
        StringBuilder csv = new StringBuilder("﻿");
        csv.append("code,type,value,expireAt,status,createdAt,priceCny\n");
        for (CardKey card : cards) {
            csv.append(csv(card.getCode())).append(',')
                    .append(card.getType().name()).append(',')
                    .append(card.getValue()).append(',')
                    .append(card.getExpireAt() == null ? "" : card.getExpireAt().format(CSV_TIME)).append(',')
                    .append(card.getStatus().name()).append(',')
                    .append(card.getCreatedAt() == null ? "" : card.getCreatedAt().format(CSV_TIME)).append(',')
                    .append(card.getPriceCny() == null ? "" : card.getPriceCny().toPlainString())
                    .append('\n');
        }
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Transactional
    public VoidCardResponse voidCard(Long adminId, Long cardId) {
        CardKey card = cardKeyMapper.selectById(cardId);
        if (card == null) {
            throw new BusinessException(ErrorCode.CARD_NOT_FOUND);
        }
        if (card.getStatus() != CardStatus.UNUSED) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "已使用或已作废卡密不可作废");
        }
        card.setStatus(CardStatus.VOID);
        cardKeyMapper.updateById(card);
        auditLogService.record(adminId, "CARD_VOID", "CARD", cardId,
                Map.of("code", card.getCode()), null);
        return new VoidCardResponse(cardId, CardStatus.VOID.name());
    }

    @Transactional
    public CardRedeemResponse redeem(Long userId, String rawCode, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "Idempotency-Key 必填");
        }
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        String idemKey = "card:idem:" + userId + ":" + idempotencyKey;
        String cached = redis.opsForValue().get(idemKey);
        if (cached != null) {
            try {
                CardRedeemResponse cachedResult = objectMapper.readValue(cached, CardRedeemResponse.class);
                return new CardRedeemResponse(cachedResult.cardType(), cachedResult.value(),
                        cachedResult.vipExpireAtAfter(), cachedResult.packBalanceAfter(), true);
            } catch (Exception ex) {
                redis.delete(idemKey);
            }
        }

        String lockToken = distributedLock.tryLock("lock:card:" + code, Duration.ofSeconds(10));
        if (lockToken == null) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }
        try {
            CardKey card = cardKeyMapper.selectOne(
                    new LambdaQueryWrapper<CardKey>().eq(CardKey::getCode, code));
            if (card == null) {
                throw new BusinessException(ErrorCode.CARD_NOT_FOUND);
            }
            if (card.getStatus() == CardStatus.USED) {
                throw new BusinessException(ErrorCode.CARD_USED);
            }
            if (card.getStatus() == CardStatus.VOID
                    || card.getStatus() == CardStatus.EXPIRED
                    || (card.getExpireAt() != null && card.getExpireAt().isBefore(timeProvider.now()))) {
                throw new BusinessException(ErrorCode.CARD_VOID_OR_EXPIRED);
            }
            if (cardKeyMapper.markUsed(card.getId(), userId, timeProvider.now()) != 1) {
                throw new BusinessException(ErrorCode.CARD_USED);
            }

            if (card.getType() == CardType.AI_QUOTA) {
                quotaService.grantAiQuota(userId, card.getValue(), card.getId(),
                        QuotaSource.REDEEM, String.valueOf(card.getId()), "card redeem");
            } else {
                quotaService.grantVip(userId, card.getValue(),
                        QuotaSource.REDEEM, String.valueOf(card.getId()), "card redeem");
            }

            User user = userMapper.selectById(userId);
            CardRedeemResponse result = new CardRedeemResponse(
                    card.getType().name(),
                    card.getValue(),
                    user == null ? null : user.getVipExpireAt(),
                    quotaService.packBalance(userId),
                    false);
            auditLogService.record(userId, "CARD_REDEEM", "CARD", card.getId(),
                    Map.of("type", card.getType().name(), "value", card.getValue()), null);
            try {
                redis.opsForValue().set(idemKey, objectMapper.writeValueAsString(result), Duration.ofHours(24));
            } catch (Exception ex) {
                log.warn("failed to cache card redemption idempotency result", ex);
            }
            return result;
        } finally {
            distributedLock.unlock("lock:card:" + code, lockToken);
        }
    }

    private void insertWithUniqueCode(String batchId, String prefix, CardType type, int value,
                                      BigDecimal priceCny, LocalDateTime expireAt,
                                      Long adminId, List<String> codes) {
        for (int attempt = 0; attempt < 5; attempt++) {
            CardKey card = new CardKey();
            card.setBatchId(batchId);
            card.setCode(codeGenerator.generate(prefix));
            card.setType(type);
            card.setValue(value);
            card.setPriceCny(priceCny);
            card.setStatus(CardStatus.UNUSED);
            card.setExpireAt(expireAt);
            card.setCreatedBy(adminId);
            try {
                cardKeyMapper.insert(card);
                codes.add(card.getCode());
                return;
            } catch (DuplicateKeyException ex) {
                log.debug("card code collision, retrying");
            }
        }
        throw new BusinessException(ErrorCode.PARAM_INVALID, "卡密编码连续冲突，请重试");
    }

    private CardKeyView toView(CardKey card) {
        return new CardKeyView(
                card.getId(), card.getBatchId(), card.getCode(), card.getType().name(),
                card.getValue(), card.getPriceCny(), card.getStatus().name(), card.getExpireAt(),
                card.getUsedBy(), card.getUsedAt(), card.getCreatedAt());
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
