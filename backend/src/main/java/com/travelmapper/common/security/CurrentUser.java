package com.travelmapper.common.security;

import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id() {
        Long userId = RequestContext.userIdOrNull();
        if (userId == null) {
            throw new BusinessException(ErrorCode.JWT_INVALID);
        }
        return userId;
    }
}
