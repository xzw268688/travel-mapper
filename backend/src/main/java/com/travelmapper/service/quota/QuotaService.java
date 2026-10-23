package com.travelmapper.service.quota;

import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.repository.entity.QuotaLedger;
import com.travelmapper.repository.entity.QuotaPack;
import com.travelmapper.repository.entity.User;
import com.travelmapper.repository.enums.QuotaSource;
import com.travelmapper.repository.enums.QuotaType;
import com.travelmapper.repository.mapper.QuotaLedgerMapper;
import com.travelmapper.repository.mapper.QuotaPackMapper;
import com.travelmapper.repository.mapper.UserMapper;
import com.travelmapper.service.config.ConfigKeys;
import com.travelmapper.service.config.ConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class QuotaService {

    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_KEY = DateTimeFormatter.BASIC_ISO_DATE;

    private static final DefaultRedisScript<Long> INCREMENT_WITH_LIMIT = new DefaultRedisScript<>(
            "local current = tonumber(redis.call('GET', KEYS[1]) or '0') "
                    + "if current >= tonumber(ARGV[1]) then return -1 end "
                    + "local next = redis.call('INCR', KEYS[1]) "
                    + "if next == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end "
                    + "return next",
            Long.class);

    private static final DefaultRedisScript<Long> DECREMENT_NOT_BELOW_ZERO = new DefaultRedisScript<>(
            "local current = tonumber(redis.call('GET', KEYS[1]) or '0') "
                    + "if current <= 0 then return 0 end "
                    + "return redis.call('DECR', KEYS[1])",
            Long.class);

    private final UserMapper userMapper;
    private final QuotaPackMapper packMapper;
    private final QuotaLedgerMapper ledgerMapper;
    private final StringRedisTemplate redis;
    private final ConfigService configService;
    private final JdbcTemplate jdbcTemplate;

    public QuotaService(UserMapper userMapper, QuotaPackMapper packMapper, QuotaLedgerMapper ledgerMapper,
                        StringRedisTemplate redis, ConfigService configService, JdbcTemplate jdbcTemplate) {
        this.userMapper = userMapper;
        this.packMapper = packMapper;
        this.ledgerMapper = ledgerMapper;
        this.redis = redis;
        this.configService = configService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public QuotaSummary getSummary(Long userId) {
        User user = requireUser(userId);
        LocalDateTime now = LocalDateTime.now(BEIJING);
        boolean vip = user.getVipExpireAt() != null && user.getVipExpireAt().isAfter(now);
        int freeLimit = configService.getInt(ConfigKeys.QUOTA_FREE_LIMIT, 3);
        int vipDailyLimit = configService.getInt(ConfigKeys.QUOTA_VIP_DAILY, 200);
        int freeUsed = redisInt(freeKey(userId));
        int vipUsed = redisInt(vipKey(userId));
        int packBalance = packBalance(userId);

        Integer expiringCount = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(granted_count - used_count), 0) FROM quota_pack "
                        + "WHERE user_id = ? AND used_count < granted_count AND expire_at > NOW() "
                        + "AND expire_at <= DATE_ADD(NOW(), INTERVAL 30 DAY)",
                Integer.class, userId);
        Timestamp nearest = jdbcTemplate.queryForObject(
                "SELECT MIN(expire_at) FROM quota_pack "
                        + "WHERE user_id = ? AND used_count < granted_count AND expire_at > NOW() "
                        + "AND expire_at <= DATE_ADD(NOW(), INTERVAL 30 DAY)",
                Timestamp.class, userId);
        QuotaSummary.PackExpiringSoon expiringSoon = expiringCount != null && expiringCount > 0
                ? new QuotaSummary.PackExpiringSoon(expiringCount, nearest.toLocalDateTime())
                : null;

        return new QuotaSummary(
                vip ? "VIP" : "FREE",
                user.getVipExpireAt(),
                freeUsed,
                freeLimit,
                packBalance,
                expiringSoon,
                vipDailyLimit,
                vipUsed,
                nextMidnight());
    }

    @Transactional
    public void checkAndDeduct(Long userId, String requestId) {
        doDeduct(userId, requestId);
    }

    @Transactional
    public QuotaDeduction deduct(Long userId, String requestId) {
        return doDeduct(userId, requestId);
    }

    /**
     * Availability check without consuming. Used before dedupe so a cached
     * generation cannot bypass an exhausted quota.
     */
    public void assertAvailable(Long userId) {
        User user = requireUser(userId);
        LocalDateTime now = LocalDateTime.now(BEIJING);
        if (user.getVipExpireAt() != null && user.getVipExpireAt().isAfter(now)) {
            int limit = configService.getInt(ConfigKeys.QUOTA_VIP_DAILY, 200);
            if (vipDailyUsed(userId) >= limit) {
                throw new BusinessException(ErrorCode.QUOTA_EXHAUSTED);
            }
            return;
        }
        if (packBalance(userId) > 0) {
            return;
        }
        int freeLimit = configService.getInt(ConfigKeys.QUOTA_FREE_LIMIT, 3);
        if (freeUsedToday(userId) >= freeLimit) {
            throw new BusinessException(ErrorCode.QUOTA_EXHAUSTED);
        }
    }

    private QuotaDeduction doDeduct(Long userId, String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "requestId 必填");
        }
        User user = requireUser(userId);
        LocalDateTime now = LocalDateTime.now(BEIJING);
        String date = LocalDate.now(BEIJING).format(DATE_KEY);
        int ttl = nightlyTtlSeconds();

        if (user.getVipExpireAt() != null && user.getVipExpireAt().isAfter(now)) {
            int limit = configService.getInt(ConfigKeys.QUOTA_VIP_DAILY, 200);
            incrementWithLimit(vipKey(userId), limit, ttl);
            recordDeduction(userId, requestId, "VIP:" + date);
            return deduction(userId, "VIP");
        }

        QuotaPack pack = packMapper.selectAvailableForUpdate(userId);
        if (pack != null) {
            for (int attempt = 0; attempt < 2; attempt++) {
                if (packMapper.incrementUsed(pack.getId()) == 1) {
                    recordDeduction(userId, requestId, "PACK:" + pack.getId());
                    appendLedger(userId, QuotaType.AI_QUOTA, -1, QuotaSource.CONSUME, requestId, "AI consume");
                    return deduction(userId, "PACK");
                }
                pack = packMapper.selectAvailableForUpdate(userId);
                if (pack == null) {
                    break;
                }
            }
        }

        int freeLimit = configService.getInt(ConfigKeys.QUOTA_FREE_LIMIT, 3);
        incrementWithLimit(freeKey(userId), freeLimit, ttl);
        recordDeduction(userId, requestId, "FREE:" + date);
        appendLedger(userId, QuotaType.AI_QUOTA, -1, QuotaSource.CONSUME, requestId, "AI consume");
        return deduction(userId, "FREE");
    }

    private QuotaDeduction deduction(Long userId, String source) {
        QuotaSummary summary = getSummary(userId);
        return new QuotaDeduction(source, summary.freeUsedToday(), summary.freeLimit(), summary.packBalance());
    }

    @Transactional
    public void refund(Long userId, String requestId) {
        String key = deductKey(userId, requestId);
        String record = redis.opsForValue().get(key);
        if (record == null) {
            return;
        }
        if (record.startsWith("PACK:")) {
            Long packId = Long.valueOf(record.substring("PACK:".length()));
            packMapper.decrementUsed(packId);
            appendLedger(userId, QuotaType.AI_QUOTA, 1, QuotaSource.REFUND, requestId, "AI failure refund");
        } else if (record.startsWith("FREE:")) {
            redis.execute(DECREMENT_NOT_BELOW_ZERO, List.of("quota:free:" + userId + ":" + record.substring(5)));
            appendLedger(userId, QuotaType.AI_QUOTA, 1, QuotaSource.REFUND, requestId, "AI failure refund");
        } else if (record.startsWith("VIP:")) {
            redis.execute(DECREMENT_NOT_BELOW_ZERO,
                    List.of("quota:daily:vip:" + userId + ":" + record.substring(4)));
        }
        redis.delete(key);
    }

    @Transactional
    public void grantAiQuota(Long userId, int count, Long cardKeyId, QuotaSource source,
                             String refId, String remark) {
        if (count <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "AI 额度必须大于 0");
        }
        int validDays = configService.getInt(ConfigKeys.QUOTA_PACK_VALID_DAYS, 365);
        QuotaPack pack = new QuotaPack();
        pack.setUserId(userId);
        pack.setCardKeyId(cardKeyId);
        pack.setGrantedCount(count);
        pack.setUsedCount(0);
        pack.setExpireAt(LocalDateTime.now(BEIJING).plusDays(validDays));
        packMapper.insert(pack);
        appendLedger(userId, QuotaType.AI_QUOTA, count, source, refId, remark);
    }

    @Transactional
    public void grantVip(Long userId, int days, QuotaSource source, String refId, String remark) {
        if (days == 0) {
            return;
        }
        extendVip(userId, days, source, refId, remark);
    }

    @Transactional
    public void adjustAiQuota(Long userId, int delta, String reason) {
        requireUser(userId);
        if (delta == 0) {
            return;
        }
        if (delta > 0) {
            grantAiQuota(userId, delta, null, QuotaSource.ADMIN, null, reason);
            return;
        }
        int needed = -delta;
        if (packBalance(userId) < needed) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "扣减后次数包余额不能小于 0");
        }
        int remaining = needed;
        while (remaining > 0) {
            QuotaPack pack = packMapper.selectAvailableForUpdate(userId);
            if (pack == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "扣减后次数包余额不能小于 0");
            }
            if (packMapper.incrementUsed(pack.getId()) == 1) {
                remaining--;
            }
        }
        appendLedger(userId, QuotaType.AI_QUOTA, delta, QuotaSource.ADMIN, null, reason);
    }

    @Transactional
    public void adjustVip(Long userId, int delta, String reason) {
        requireUser(userId);
        if (delta == 0) {
            return;
        }
        extendVip(userId, delta, QuotaSource.ADMIN, null, reason);
    }

    private void extendVip(Long userId, int days, QuotaSource source, String refId, String remark) {
        User user = requireUser(userId);
        LocalDateTime now = LocalDateTime.now(BEIJING);
        LocalDateTime base = user.getVipExpireAt() != null && user.getVipExpireAt().isAfter(now)
                ? user.getVipExpireAt() : now;
        LocalDateTime next = base.plusDays(days);
        if (next.isBefore(now)) {
            next = now;
        }
        user.setVipExpireAt(next);
        userMapper.updateById(user);
        appendLedger(userId, QuotaType.VIP, days, source, refId, remark);
    }

    public int packBalance(Long userId) {
        Integer balance = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(granted_count - used_count), 0) FROM quota_pack "
                        + "WHERE user_id = ? AND used_count < granted_count AND expire_at > NOW()",
                Integer.class, userId);
        return balance == null ? 0 : balance;
    }

    public int freeUsedToday(Long userId) {
        return redisInt(freeKey(userId));
    }

    public int vipDailyUsed(Long userId) {
        return redisInt(vipKey(userId));
    }

    public void evictUserKeys(Long userId) {
        try {
            Set<String> keys = redis.keys("quota:*:" + userId + ":*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
            redis.delete("quota:pack:" + userId);
        } catch (Exception ex) {
            log.debug("failed to evict quota keys", ex);
        }
    }

    private void incrementWithLimit(String key, int limit, int ttlSeconds) {
        Long result = redis.execute(INCREMENT_WITH_LIMIT, List.of(key),
                String.valueOf(limit), String.valueOf(Math.max(1, ttlSeconds)));
        if (result == null || result < 0) {
            throw new BusinessException(ErrorCode.QUOTA_EXHAUSTED);
        }
    }

    private void recordDeduction(Long userId, String requestId, String source) {
        redis.opsForValue().set(deductKey(userId, requestId), source, Duration.ofMinutes(10));
        redis.opsForValue().set("quota:req:" + userId + ":" + requestId, "1", Duration.ofMinutes(5));
    }

    private void appendLedger(Long userId, QuotaType type, int delta, QuotaSource source,
                              String refId, String remark) {
        QuotaLedger ledger = new QuotaLedger();
        ledger.setUserId(userId);
        ledger.setType(type);
        ledger.setDelta(delta);
        ledger.setSource(source);
        ledger.setRefId(refId);
        ledger.setRemark(remark);
        ledgerMapper.insert(ledger);
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.JWT_INVALID);
        }
        return user;
    }

    private int redisInt(String key) {
        String value = redis.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String freeKey(Long userId) {
        return "quota:free:" + userId + ":" + LocalDate.now(BEIJING).format(DATE_KEY);
    }

    private String vipKey(Long userId) {
        return "quota:daily:vip:" + userId + ":" + LocalDate.now(BEIJING).format(DATE_KEY);
    }

    private String deductKey(Long userId, String requestId) {
        return "quota:deduct:" + userId + ":" + requestId;
    }

    private LocalDateTime nextMidnight() {
        return LocalDate.now(BEIJING).plusDays(1).atStartOfDay();
    }

    private int nightlyTtlSeconds() {
        long seconds = Duration.between(LocalDateTime.now(BEIJING), nextMidnight()).getSeconds();
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, seconds));
    }
}
