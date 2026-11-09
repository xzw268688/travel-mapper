package com.travelmapper.service.monitor;

import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.common.api.PageRequest;
import com.travelmapper.common.api.PageResult;
import com.travelmapper.common.security.OnlineUserRegistry;
import com.travelmapper.common.security.UserStatusCache;
import com.travelmapper.controller.admin.dto.MonitorDtos;
import com.travelmapper.repository.entity.User;
import com.travelmapper.repository.mapper.UserMapper;
import com.travelmapper.service.audit.AuditLogService;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import javax.sql.DataSource;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class AdminMonitorService {

    private final OnlineUserRegistry onlineUserRegistry;
    private final UserMapper userMapper;
    private final UserStatusCache userStatusCache;
    private final AuditLogService auditLogService;
    private final DataSource dataSource;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    public AdminMonitorService(OnlineUserRegistry onlineUserRegistry, UserMapper userMapper,
                               UserStatusCache userStatusCache,
                               AuditLogService auditLogService,
                               DataSource dataSource,
                               io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        this.onlineUserRegistry = onlineUserRegistry;
        this.userMapper = userMapper;
        this.userStatusCache = userStatusCache;
        this.auditLogService = auditLogService;
        this.dataSource = dataSource;
        this.meterRegistry = meterRegistry;
    }

    public PageResult<MonitorDtos.OnlineUserView> onlineUsers(String keyword, String role, String ip,
                                                              PageRequest request) {
        request.validate();
        List<OnlineUserRegistry.OnlineUser> online = onlineUserRegistry.list();
        Map<Long, User> users = new HashMap<>();
        if (!online.isEmpty()) {
            List<Long> ids = online.stream().map(OnlineUserRegistry.OnlineUser::userId).toList();
            for (User user : userMapper.selectBatchIds(ids)) {
                users.put(user.getId(), user);
            }
        }
        String keywordFilter = keyword == null || keyword.isBlank()
                ? null : keyword.trim().toLowerCase(Locale.ROOT);
        String roleFilter = role == null || role.isBlank()
                ? null : role.trim().toUpperCase(Locale.ROOT);
        String ipFilter = ip == null || ip.isBlank() ? null : ip.trim();
        List<MonitorDtos.OnlineUserView> filtered = new ArrayList<>();
        for (OnlineUserRegistry.OnlineUser item : online) {
            User user = users.get(item.userId());
            String username = user == null ? null : user.getUsername();
            String nickname = user == null ? null : user.getNickname();
            String userRole = user != null && user.getRole() != null
                    ? user.getRole().name() : item.role();
            if (keywordFilter != null
                    && (username == null || !username.toLowerCase(Locale.ROOT).contains(keywordFilter))
                    && (nickname == null || !nickname.toLowerCase(Locale.ROOT).contains(keywordFilter))) {
                continue;
            }
            if (roleFilter != null && !roleFilter.equals(userRole)) {
                continue;
            }
            if (ipFilter != null && (item.ip() == null || !item.ip().contains(ipFilter))) {
                continue;
            }
            filtered.add(new MonitorDtos.OnlineUserView(
                    item.userId(),
                    username,
                    nickname,
                    userRole,
                    item.ip(),
                    item.userAgent(),
                    LocalDateTime.ofInstant(Instant.ofEpochSecond(item.lastSeenEpoch()),
                            ZoneId.systemDefault())));
        }
        int from = (int) Math.min(request.offset(), filtered.size());
        int to = Math.min(from + request.getSize(), filtered.size());
        return PageResult.of(filtered.size(), request, filtered.subList(from, to));
    }

    public void kick(Long adminId, Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "用户不存在");
        }
        int nextVersion = (user.getTokenVersion() == null ? 1 : user.getTokenVersion()) + 1;
        user.setTokenVersion(nextVersion);
        userMapper.updateById(user);
        userStatusCache.evict(userId);
        onlineUserRegistry.remove(userId);
        auditLogService.record(adminId, "USER_KICK", "USER", userId,
                Map.of("tokenVersion", nextVersion), null);
    }

    public MonitorDtos.ServerMetrics server() {
        boolean partial = false;
        double systemCpu = 0;
        double processCpu = 0;
        long totalMemory = 0;
        long freeMemory = 0;
        if (ManagementFactory.getOperatingSystemMXBean()
                instanceof com.sun.management.OperatingSystemMXBean osBean) {
            systemCpu = safeLoad(osBean.getCpuLoad());
            processCpu = safeLoad(osBean.getProcessCpuLoad());
            totalMemory = Math.max(0, osBean.getTotalMemorySize());
            freeMemory = Math.max(0, osBean.getFreeMemorySize());
        } else {
            partial = true;
        }

        String osName = System.getProperty("os.name", "");
        String osVersion = System.getProperty("os.version", "");
        String osArch = System.getProperty("os.arch", "");
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int logical = availableProcessors;
        int physical = availableProcessors;
        long systemUptime = 0;
        List<MonitorDtos.DiskView> disks = new ArrayList<>();
        try {
            SystemInfo systemInfo = new SystemInfo();
            HardwareAbstractionLayer hardware = systemInfo.getHardware();
            OperatingSystem os = systemInfo.getOperatingSystem();
            osName = os.getFamily();
            if (os.getVersionInfo() != null) {
                osVersion = os.getVersionInfo().getVersion();
            }
            if (os.getBitness() > 0) {
                osArch = os.getBitness() + "-bit";
            }
            systemUptime = os.getSystemUptime();
            if (hardware.getProcessor() != null) {
                logical = hardware.getProcessor().getLogicalProcessorCount();
                physical = hardware.getProcessor().getPhysicalProcessorCount();
            }
            if (hardware.getMemory() != null) {
                totalMemory = hardware.getMemory().getTotal();
                freeMemory = hardware.getMemory().getAvailable();
            }
            for (OSFileStore store : os.getFileSystem().getFileStores()) {
                if (store.getTotalSpace() > 0) {
                    disks.add(new MonitorDtos.DiskView(store.getName(), store.getMount(),
                            store.getTotalSpace(), store.getUsableSpace()));
                }
            }
        } catch (RuntimeException ex) {
            partial = true;
        }

        return new MonitorDtos.ServerMetrics(
                osName, osVersion, osArch, availableProcessors, logical, physical,
                systemCpu, processCpu, totalMemory, freeMemory, systemUptime,
                disks, jvmMetrics(), partial);
    }

    public MonitorDtos.DatabaseMetrics database() {
        if (!(dataSource instanceof HikariDataSource hikari)) {
            return new MonitorDtos.DatabaseMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, true);
        }
        HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
        if (pool == null) {
            return new MonitorDtos.DatabaseMetrics(0, 0, 0, 0,
                    hikari.getMaximumPoolSize(), hikari.getMinimumIdle(),
                    hikari.getConnectionTimeout(), 0, 0, 0, true);
        }
        Timer timer = meterRegistry.find("hikaricp.connections.acquire").timer();
        long acquireCount = timer == null ? 0 : timer.count();
        double average = timer == null || acquireCount == 0
                ? 0 : timer.totalTime(TimeUnit.MILLISECONDS) / acquireCount;
        double max = timer == null ? 0 : timer.max(TimeUnit.MILLISECONDS);
        return new MonitorDtos.DatabaseMetrics(
                pool.getActiveConnections(),
                pool.getIdleConnections(),
                pool.getTotalConnections(),
                pool.getThreadsAwaitingConnection(),
                hikari.getMaximumPoolSize(),
                hikari.getMinimumIdle(),
                hikari.getConnectionTimeout(),
                acquireCount,
                round(average),
                round(max),
                timer == null);
    }

    private MonitorDtos.JvmView jvmMetrics() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        long gcCount = 0;
        long gcTime = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gc.getCollectionCount() > 0) {
                gcCount += gc.getCollectionCount();
            }
            if (gc.getCollectionTime() > 0) {
                gcTime += gc.getCollectionTime();
            }
        }
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        return new MonitorDtos.JvmView(
                heap.getUsed(),
                Math.max(0, heap.getMax()),
                heap.getCommitted(),
                nonHeap.getUsed(),
                threads.getThreadCount(),
                threads.getPeakThreadCount(),
                gcCount,
                gcTime,
                runtime.getUptime(),
                System.getProperty("java.version", ""),
                System.getProperty("java.vendor", ""),
                LocalDateTime.ofInstant(Instant.ofEpochMilli(runtime.getStartTime()),
                        ZoneId.systemDefault()));
    }

    private double safeLoad(double value) {
        if (Double.isNaN(value) || value < 0) {
            return 0;
        }
        return Math.min(1, value);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}