package com.travelmapper;

import com.travelmapper.service.retention.RetentionCleanupService;
import com.travelmapper.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RetentionCleanupServiceTest extends AbstractIntegrationTest {

    @Autowired
    private RetentionCleanupService cleanupService;

    @Test
    void deletesOnlyExpiredRows() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String oldAudit = "TEST_OLD_" + suffix;
        String newAudit = "TEST_NEW_" + suffix;
        String oldLogin = "old_" + suffix;
        String newLogin = "new_" + suffix;
        String oldQuotaUser = "9000000" + suffix.replaceAll("[^0-9]", "1");

        try {
            jdbcTemplate.update("INSERT INTO audit_log (user_id, action, created_at) "
                    + "VALUES (1, ?, DATE_SUB(NOW(), INTERVAL 31 DAY))", oldAudit);
            jdbcTemplate.update("INSERT INTO audit_log (user_id, action, created_at) "
                    + "VALUES (1, ?, NOW())", newAudit);
            jdbcTemplate.update("INSERT INTO login_log (user_id, username, success, reason, created_at) "
                    + "VALUES (1, ?, 1, 'SUCCESS', DATE_SUB(NOW(), INTERVAL 91 DAY))", oldLogin);
            jdbcTemplate.update("INSERT INTO login_log (user_id, username, success, reason, created_at) "
                    + "VALUES (1, ?, 1, 'SUCCESS', NOW())", newLogin);

            cleanupService.cleanupAuditLogs();
            cleanupService.cleanupLoginLogs();

            assertThat(count("audit_log", "action = ?", oldAudit)).isZero();
            assertThat(count("audit_log", "action = ?", newAudit)).isEqualTo(1);
            assertThat(count("login_log", "username = ?", oldLogin)).isZero();
            assertThat(count("login_log", "username = ?", newLogin)).isEqualTo(1);
        } finally {
            jdbcTemplate.update("DELETE FROM audit_log WHERE action IN (?, ?)", oldAudit, newAudit);
            jdbcTemplate.update("DELETE FROM login_log WHERE username IN (?, ?)", oldLogin, newLogin);
        }
    }

    @Test
    void expiresCardsAndCleansSharesQuotaPacksAndEmailTokens() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String oldCode = "OLD-" + suffix;
        String newCode = "NEW-" + suffix;
        String oldShare = "old-share-" + suffix;
        String expiredShare = "expired-share-" + suffix;
        String activeShare = "active-share-" + suffix;
        String oldToken = "old-token-" + suffix;
        String newToken = "new-token-" + suffix;

        try {
            jdbcTemplate.update("INSERT INTO card_key (batch_id, code, type, value, status, expire_at, created_by) "
                    + "VALUES ('TEST', ?, 'AI_QUOTA', 1, 'UNUSED', DATE_SUB(NOW(), INTERVAL 1 DAY), 1)", oldCode);
            jdbcTemplate.update("INSERT INTO card_key (batch_id, code, type, value, status, expire_at, created_by) "
                    + "VALUES ('TEST', ?, 'AI_QUOTA', 1, 'UNUSED', DATE_ADD(NOW(), INTERVAL 1 DAY), 1)", newCode);

            jdbcTemplate.update("INSERT INTO note_share (note_id, token, revoked, created_at) "
                    + "VALUES (1, ?, 1, NOW())", oldShare);
            jdbcTemplate.update("INSERT INTO note_share (note_id, token, expire_at) "
                    + "VALUES (1, ?, DATE_SUB(NOW(), INTERVAL 40 DAY))", expiredShare);
            jdbcTemplate.update("INSERT INTO note_share (note_id, token, expire_at) "
                    + "VALUES (1, ?, DATE_ADD(NOW(), INTERVAL 1 DAY))", activeShare);

            jdbcTemplate.update("INSERT INTO quota_pack (user_id, granted_count, used_count, expire_at) "
                    + "VALUES (1, 1, 0, DATE_SUB(NOW(), INTERVAL 100 DAY))");

            jdbcTemplate.update("INSERT INTO email_token (user_id, email, token, type, expire_at) "
                    + "VALUES (1, 'retention@example.com', ?, 'VERIFY', DATE_SUB(NOW(), INTERVAL 40 DAY))", oldToken);
            jdbcTemplate.update("INSERT INTO email_token (user_id, email, token, type, expire_at) "
                    + "VALUES (1, 'retention@example.com', ?, 'VERIFY', DATE_ADD(NOW(), INTERVAL 1 DAY))", newToken);

            cleanupService.expireCardKeys();
            cleanupService.cleanupNoteShares();
            cleanupService.cleanupQuotaPacks();
            cleanupService.cleanupEmailTokens();

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM card_key WHERE code = ?", String.class, oldCode))
                    .isEqualTo("EXPIRED");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM card_key WHERE code = ?", String.class, newCode))
                    .isEqualTo("UNUSED");

            assertThat(count("note_share", "token = ?", oldShare)).isZero();
            assertThat(count("note_share", "token = ?", expiredShare)).isZero();
            assertThat(count("note_share", "token = ?", activeShare)).isEqualTo(1);
            assertThat(count("email_token", "token = ?", oldToken)).isZero();
            assertThat(count("email_token", "token = ?", newToken)).isEqualTo(1);
            assertThat(count("quota_pack", "expire_at < DATE_SUB(NOW(), INTERVAL 90 DAY)"))
                    .isZero();
        } finally {
            jdbcTemplate.update("DELETE FROM card_key WHERE code IN (?, ?)", oldCode, newCode);
            jdbcTemplate.update("DELETE FROM note_share WHERE token IN (?, ?, ?)",
                    oldShare, expiredShare, activeShare);
            jdbcTemplate.update("DELETE FROM email_token WHERE token IN (?, ?)", oldToken, newToken);
            jdbcTemplate.update("DELETE FROM quota_pack WHERE granted_count = 1 AND used_count = 0 "
                    + "AND expire_at < DATE_SUB(NOW(), INTERVAL 90 DAY)");
        }
    }

    private long count(String table, String where, Object... args) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `" + table + "` WHERE " + where, Long.class, args);
        return value == null ? 0 : value;
    }
}