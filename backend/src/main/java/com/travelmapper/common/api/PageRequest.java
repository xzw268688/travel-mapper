package com.travelmapper.common.api;

import lombok.Data;

@Data
public class PageRequest {

    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 100;

    private int page = 1;
    private int size = DEFAULT_SIZE;
    private String sort;

    public void validate() {
        if (page < 1) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "page 必须大于等于 1");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "size 必须在 1 到 100 之间");
        }
    }

    public long offset() {
        return (long) (page - 1) * size;
    }
}
