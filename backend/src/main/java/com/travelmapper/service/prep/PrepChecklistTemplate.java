package com.travelmapper.service.prep;

import java.util.List;
import java.util.Optional;

/** 行前清单默认十项；顺序与前端展示一致。 */
public final class PrepChecklistTemplate {

    public record DefaultItem(
            String key,
            String title,
            String description,
            int daysBefore,
            int sortOrder) {
    }

    public static final List<DefaultItem> ITEMS = List.of(
            new DefaultItem("VISA", "签证",
                    "确认签证类型、所需材料和办理时长；确保护照有效期覆盖整个行程。", 60, 10),
            new DefaultItem("FLIGHT", "机票",
                    "对比直飞/转机、行李额和退改签；廉航注意托运行李费用。", 45, 20),
            new DefaultItem("HOTEL", "住宿",
                    "确认位置、交通、取消政策、独立卫浴和入住时间。", 30, 30),
            new DefaultItem("INSURANCE", "保险",
                    "覆盖医疗、意外、行李延误/丢失；保额满足目的地要求。", 30, 40),
            new DefaultItem("SIM", "电话卡 / eSIM",
                    "对比本地 SIM 卡与 eSIM，确认流量、覆盖范围和激活方式。", 14, 50),
            new DefaultItem("CURRENCY", "换汇与银行卡",
                    "准备少量现金，带银联/Visa/MasterCard，确认境外取现手续费。", 14, 60),
            new DefaultItem("PLUG", "转换头 + 插排",
                    "确认目的地插头标准与电压，带万能转换头和插排。", 7, 70),
            new DefaultItem("APPS", "App 准备",
                    "地图、翻译、公共交通、支付和当地生活类 App。", 7, 80),
            new DefaultItem("PACKING", "必备物品",
                    "充电宝、雨具、舒适步行鞋、常用药品、防晒等。", 7, 90),
            new DefaultItem("CUSTOMS", "入境卡与海关",
                    "了解入境卡/电子申报、现金申报限额和禁带物品。", 1, 100)
    );

    private PrepChecklistTemplate() {
    }

    public static Optional<DefaultItem> find(String key) {
        return ITEMS.stream().filter(item -> item.key().equals(key)).findFirst();
    }

    public static boolean isDefault(String key) {
        return find(key).isPresent();
    }
}