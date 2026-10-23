package com.travelmapper.repository.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChecklistItemTest {

    @Test
    void hasExactlySevenFixedItemsInOrder() {
        assertThat(ChecklistItem.values()).hasSize(7);
        assertThat(ChecklistItem.values())
                .extracting(Enum::name)
                .containsExactly("VISA", "FLIGHT", "HOTEL", "INSURANCE", "CURRENCY", "PLUG", "SIM");
        assertThat(ChecklistItem.VISA.getLabel()).isEqualTo("签证已办");
        assertThat(ChecklistItem.SIM.getLabel()).isEqualTo("电话卡/流量已办");
    }
}
