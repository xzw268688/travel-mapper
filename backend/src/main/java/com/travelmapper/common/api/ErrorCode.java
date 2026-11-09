package com.travelmapper.common.api;

import lombok.Getter;

@Getter
public enum ErrorCode {

    OK(0, "ok", 200),

    USERNAME_OR_PASSWORD(1001, "用户名或密码错误", 401),
    EMAIL_NOT_VERIFIED(1002, "邮箱未验证", 403),
    ACCOUNT_BANNED(1003, "账号已被封禁", 403),
    TOKEN_INVALID(1004, "验证码无效或已过期", 400),
    EMAIL_SEND_TOO_FREQUENT(1005, "邮件发送过于频繁", 429),
    JWT_INVALID(1006, "JWT 无效或过期", 401),
    ACCOUNT_DELETED(1007, "账号已注销", 403),
    ACCOUNT_ALREADY_EXISTS(1008, "用户名或邮箱已存在", 409),

    PARAM_INVALID(2001, "参数校验失败", 400),
    COUNTRY_OR_CITY_INVALID(2002, "国家/城市不存在或已停用", 400),
    ITINERARY_NOT_FOUND(2003, "行程不存在或无权访问", 404),
    NOTE_NOT_FOUND(2004, "游记不存在或无权访问", 404),
    EXPENSE_NOT_FOUND(2005, "消费记录不存在或无权访问", 404),
    MANUAL_SPOT_LIMIT(2006, "手动景点已达上限", 400),
    SHARE_INVALID(2007, "分享令牌无效或已失效", 410),
    CARD_NOT_FOUND(2008, "卡密不存在", 404),
    CARD_USED(2009, "卡密已使用", 409),
    CARD_VOID_OR_EXPIRED(2010, "卡密已作废或过期", 410),
    QUOTA_EXHAUSTED(2011, "额度耗尽", 429),
    AI_UNAVAILABLE(2012, "AI 服务不可用", 503),
    SEARCH_UNAVAILABLE(2013, "搜索服务不可用", 503),
    RATE_NOT_CONFIGURED(2014, "未配置汇率", 200),
    TRIP_NOT_FOUND(2015, "旅行不存在或无权访问", 404),
    SPOT_NOT_FOUND(2016, "景点不存在或无权访问", 404),
    TRIP_HAS_CONTENT(2017, "旅行下存在挂载数据，需 force=true", 409),
    IMAGE_COUNT_LIMIT(2018, "图片数量超限", 400),
    IMAGE_SIZE_LIMIT(2019, "单图超限", 400),
    CONTENT_SIZE_LIMIT(2020, "正文超限", 400),
    IMAGE_TYPE_UNSUPPORTED(2021, "不支持的图片类型", 400),
    CHECKLIST_ITEM_INVALID(2022, "清单项不在固定枚举内", 400),
    SHARE_CREATE_FAILED(2023, "分享令牌创建失败", 500),
    CONTENT_TAKEDOWN(2024, "内容已下架，不可操作", 403),
    CITY_REFERENCED(2025, "城市已被引用，不能物理删除", 409),
    OFFICIAL_LINK_LIMIT(2026, "该国官方链接已达上限", 400),
    MEDIA_OBJECT_INVALID(2027, "媒体对象无效或不属于当前用户", 400),
    VIDEO_COUNT_LIMIT(2028, "视频数量超限", 400),
    VIDEO_SIZE_LIMIT(2029, "单个视频超限", 400),
    VIDEO_TYPE_UNSUPPORTED(2030, "不支持的视频类型", 400),
    FEEDBACK_NOT_FOUND(2031, "反馈不存在", 404),
    REPORT_NOT_FOUND(2032, "举报不存在", 404),
    RATE_FEED_UNAVAILABLE(2033, "汇率数据源不可用", 503),
    ANNOUNCEMENT_NOT_FOUND(2034, "公告不存在或未生效", 404),

    DUPLICATE_REQUEST(3001, "重复请求 / 相同请求正在处理", 409),
    CONCURRENT_CONFLICT(3002, "并发操作冲突", 409),

    FORBIDDEN_RESOURCE(4001, "无权访问该资源", 403),
    FORBIDDEN_ADMIN(4002, "需要管理员权限", 403),

    INTERNAL_ERROR(5001, "系统内部错误", 500),
    DATABASE_ERROR(5002, "数据库操作失败", 500),
    STORAGE_UNAVAILABLE(5003, "存储服务不可用", 503),
    RATE_LIMITED(5004, "限流拦截", 429),

    /** Infrastructure-only code for unmatched routes; not part of the business error catalogue. */
    ROUTE_NOT_FOUND(4040, "请求的资源不存在", 404);

    private final int code;
    private final String message;
    private final int httpStatus;

    ErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
