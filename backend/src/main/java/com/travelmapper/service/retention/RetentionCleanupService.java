package com.travelmapper.service.retention;

import com.travelmapper.common.time.TimeProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 数据保留清理：与 DB 文档 8.3 对齐。
 * 每个任务独立调度、独立捕获异常；单个任务失败不影响其他任务。
 */
@Slf4j
@Service
public class RetentionCleanupService {

    private final JdbcTemplate jdbcTemplate;
    private final TimeProvider timeProvider;

    public RetentionCleanupService(JdbcTemplate jdbcTemplate, TimeProvider timeProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.timeProvider = timeProvider;
    }

    @Scheduled(cron = "${app.retention.email-token-cron:0 0 * * * *}")
    public void cleanupEmailTokens() {
        execute("email_token",
                "DELETE FROM email_token WHERE expire_at < ?",
                timeProvider.now().minusDays(30));
    }

    @Scheduled(cron = "${app.retention.card-expire-cron:0 0 3 * * *}")
    public void expireCardKeys() {
        execute("card_key",
                "UPDATE card_key SET status = 'EXPIRED' "
                        + "WHERE status = 'UNUSED' AND expire_at IS NOT NULL AND expire_at < ?",
                timeProvider.now());
    }

    @Scheduled(cron = "${app.retention.quota-pack-cron:0 5 3 * * *}")
    public void cleanupQuotaPacks() {
        execute("quota_pack",
                "DELETE FROM quota_pack WHERE expire_at < ?",
                timeProvider.now().minusDays(90));
    }

    @Scheduled(cron = "${app.retention.audit-log-cron:0 10 3 * * *}")
    public void cleanupAuditLogs() {
        execute("audit_log",
                "DELETE FROM audit_log WHERE created_at < ?",
                timeProvider.now().minusDays(30));
    }

    @Scheduled(cron = "${app.retention.login-log-cron:0 15 3 * * *}")
    public void cleanupLoginLogs() {
        execute("login_log",
                "DELETE FROM login_log WHERE created_at < ?",
                timeProvider.now().minusDays(90));
    }

    @Scheduled(cron = "${app.retention.share-token-cron:0 20 3 * * *}")
    public void cleanupNoteShares() {
        execute("note_share",
                "DELETE FROM note_share WHERE revoked = 1 "
                        + "OR (expire_at IS NOT NULL AND expire_at < ?)",
                timeProvider.now().minusDays(30));
    }

    private void execute(String task, String sql, LocalDateTime cutoff) {
        try {
            int affected = jdbcTemplate.update(sql, cutoff);
            if (affected > 0) {
                log.info("retention cleanup: task={} affected={}", task, affected);
            }
        } catch (Exception ex) {
            log.warn("retention cleanup failed: task={}", task, ex);
        }
    }
}