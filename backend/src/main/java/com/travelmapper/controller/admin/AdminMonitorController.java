package com.travelmapper.controller.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.api.PageRequest;
import com.travelmapper.common.api.PageResult;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.controller.admin.dto.MonitorDtos;
import com.travelmapper.service.monitor.AdminApiDocsService;
import com.travelmapper.service.monitor.AdminCacheMonitorService;
import com.travelmapper.service.monitor.AdminMonitorService;
import com.travelmapper.service.monitor.AdminStorageMonitorService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Monitor")
@RestController
@RequestMapping("/api/admin/monitor")
public class AdminMonitorController {

    private final AdminMonitorService monitorService;
    private final AdminCacheMonitorService cacheMonitorService;
    private final AdminStorageMonitorService storageMonitorService;
    private final AdminApiDocsService apiDocsService;

    public AdminMonitorController(AdminMonitorService monitorService,
                                  AdminCacheMonitorService cacheMonitorService,
                                  AdminStorageMonitorService storageMonitorService,
                                  AdminApiDocsService apiDocsService) {
        this.monitorService = monitorService;
        this.cacheMonitorService = cacheMonitorService;
        this.storageMonitorService = storageMonitorService;
        this.apiDocsService = apiDocsService;
    }

    @GetMapping("/online-users")
    public ApiResponse<PageResult<MonitorDtos.OnlineUserView>> onlineUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String ip,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest request = new PageRequest();
        request.setPage(page);
        request.setSize(size);
        return ApiResponse.ok(monitorService.onlineUsers(keyword, role, ip, request));
    }

    @DeleteMapping("/online-users/{userId}")
    public ApiResponse<Void> kick(@PathVariable Long userId) {
        monitorService.kick(CurrentUser.id(), userId);
        return ApiResponse.ok();
    }

    @GetMapping("/server")
    public ApiResponse<MonitorDtos.ServerMetrics> server() {
        return ApiResponse.ok(monitorService.server());
    }

    @GetMapping("/database")
    public ApiResponse<MonitorDtos.DatabaseMetrics> database() {
        return ApiResponse.ok(monitorService.database());
    }

    @GetMapping("/cache")
    public ApiResponse<MonitorDtos.CacheMetrics> cache() {
        return ApiResponse.ok(cacheMonitorService.metrics());
    }

    @DeleteMapping("/cache/{group}")
    public ApiResponse<MonitorDtos.CacheClearResponse> clearCache(@PathVariable String group) {
        return ApiResponse.ok(cacheMonitorService.clear(CurrentUser.id(), group));
    }

    @GetMapping("/storage")
    public ApiResponse<MonitorDtos.StorageMetrics> storage() {
        return ApiResponse.ok(storageMonitorService.metrics());
    }

    @GetMapping("/storage/stats")
    public ApiResponse<MonitorDtos.StorageStats> storageStats() {
        return ApiResponse.ok(storageMonitorService.stats());
    }

    @GetMapping("/api-docs")
    public ApiResponse<JsonNode> apiDocs() {
        return ApiResponse.ok(apiDocsService.apiDocs());
    }
}