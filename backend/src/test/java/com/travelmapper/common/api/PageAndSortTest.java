package com.travelmapper.common.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageAndSortTest {

    @Test
    void rejectsInvalidPageAndSize() {
        PageRequest page = new PageRequest();
        page.setPage(0);
        assertThatThrownBy(page::validate)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);

        PageRequest size = new PageRequest();
        size.setSize(101);
        assertThatThrownBy(size::validate).isInstanceOf(BusinessException.class);
    }

    @Test
    void computesOffset() {
        PageRequest page = new PageRequest();
        page.setPage(3);
        page.setSize(20);
        assertThat(page.offset()).isEqualTo(40);
    }

    @Test
    void resolvesWhitelistedSort() {
        SortWhitelist whitelist = SortWhitelist.of("createdAt", "name")
                .allow("cityName", "name");
        assertThat(whitelist.resolve("createdAt:desc,name")).isEqualTo("createdAt DESC, name ASC");
        assertThat(whitelist.resolve("cityName")).isEqualTo("name ASC");
        assertThat(whitelist.resolve(null)).isNull();
    }

    @Test
    void rejectsUnknownSortAndDirection() {
        SortWhitelist whitelist = SortWhitelist.of("createdAt");
        assertThatThrownBy(() -> whitelist.resolve("password:desc"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> whitelist.resolve("createdAt:sideways"))
                .isInstanceOf(BusinessException.class);
    }
}
