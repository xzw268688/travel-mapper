package com.travelmapper.common.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves API sort expressions (e.g. "createdAt:desc,name") against an explicit
 * whitelist so user input can never reach an ORDER BY clause unchecked.
 */
public final class SortWhitelist {

    private final Map<String, String> allowed = new LinkedHashMap<>();

    private SortWhitelist() {
    }

    public static SortWhitelist of(String... apiFieldsToSameColumn) {
        SortWhitelist whitelist = new SortWhitelist();
        for (String field : apiFieldsToSameColumn) {
            whitelist.allowed.put(field, field);
        }
        return whitelist;
    }

    public SortWhitelist allow(String apiField, String column) {
        allowed.put(apiField, column);
        return this;
    }

    public String resolve(String sort) {
        if (sort == null || sort.isBlank()) {
            return null;
        }
        List<String> clauses = new ArrayList<>();
        for (String raw : sort.split(",", -1)) {
            String part = raw.trim();
            if (part.isEmpty()) {
                throw invalid();
            }
            String[] pieces = part.split(":", 2);
            String field = pieces[0].trim();
            String direction = pieces.length == 2 ? pieces[1].trim().toUpperCase(Locale.ROOT) : "ASC";
            String column = allowed.get(field);
            if (column == null || (!"ASC".equals(direction) && !"DESC".equals(direction))) {
                throw invalid();
            }
            clauses.add(column + " " + direction);
        }
        return String.join(", ", clauses);
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.PARAM_INVALID, "非法排序字段或方向");
    }
}
