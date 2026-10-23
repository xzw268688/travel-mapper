package com.travelmapper.common.api;

import java.util.List;

public record PageResult<T>(long total, int page, int size, List<T> list) {

    public static <T> PageResult<T> of(long total, PageRequest request, List<T> list) {
        return new PageResult<>(total, request.getPage(), request.getSize(), list);
    }
}
