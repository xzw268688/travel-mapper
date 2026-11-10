package com.travelmapper.repository.enums;

import lombok.Getter;

/**
 * The seven fixed pre-trip checklist items. Intentionally code-only: no table is created.
 */
@Getter
public enum ChecklistItem {
    VISA("签证已办"),
    FLIGHT("机票已订"),
    HOTEL("住宿已订"),
    INSURANCE("保险已买"),
    CURRENCY("货币已换"),
    PLUG("插头转换器已备"),
    SIM("电话卡/流量已办");

    private final String label;

    ChecklistItem(String label) {
        this.label = label;
    }
}
