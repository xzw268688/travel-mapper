package com.travelmapper.common.api;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    void containsDocumentedBusinessCodes() {
        Set<Integer> codes = Arrays.stream(ErrorCode.values())
                .map(ErrorCode::getCode)
                .collect(Collectors.toSet());

        assertThat(codes).contains(
                0,
                1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008,
                2001, 2002, 2003, 2004, 2005, 2006, 2007, 2008, 2009, 2010,
                2011, 2012, 2013, 2014, 2015, 2016, 2017, 2018, 2019, 2020,
                2021, 2022, 2023, 2024, 2025, 2026, 2027, 2028, 2029, 2030, 2031, 2032, 2033,
                3001, 3002, 4001, 4002, 5001, 5002, 5003, 5004);
    }

    @Test
    void mapsHttpStatusesForCriticalCodes() {
        assertThat(ErrorCode.OK.getHttpStatus()).isEqualTo(200);
        assertThat(ErrorCode.JWT_INVALID.getHttpStatus()).isEqualTo(401);
        assertThat(ErrorCode.FORBIDDEN_ADMIN.getHttpStatus()).isEqualTo(403);
        assertThat(ErrorCode.ITINERARY_NOT_FOUND.getHttpStatus()).isEqualTo(404);
        assertThat(ErrorCode.CARD_USED.getHttpStatus()).isEqualTo(409);
        assertThat(ErrorCode.SHARE_INVALID.getHttpStatus()).isEqualTo(410);
        assertThat(ErrorCode.QUOTA_EXHAUSTED.getHttpStatus()).isEqualTo(429);
        assertThat(ErrorCode.AI_UNAVAILABLE.getHttpStatus()).isEqualTo(503);
    }
}
