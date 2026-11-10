package com.travelmapper.common.time;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 由应用统一写入审计时间，避免数据库默认 CURRENT_TIMESTAMP 与应用时钟混用。
 * 插入时只填充为 null 的字段，保留调用方显式设置的值；更新时强制刷新 updatedAt。
 */
@Component
public class AuditFieldMetaObjectHandler implements MetaObjectHandler {

    private final TimeProvider timeProvider;

    public AuditFieldMetaObjectHandler(TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = timeProvider.now();
        fillIfNull(metaObject, "createdAt", now);
        fillIfNull(metaObject, "updatedAt", now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        if (metaObject.hasSetter("updatedAt")) {
            metaObject.setValue("updatedAt", timeProvider.now());
        }
    }

    private void fillIfNull(MetaObject metaObject, String field, LocalDateTime value) {
        if (metaObject.hasSetter(field) && metaObject.getValue(field) == null) {
            metaObject.setValue(field, value);
        }
    }
}