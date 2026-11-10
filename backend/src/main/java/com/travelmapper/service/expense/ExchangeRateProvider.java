package com.travelmapper.service.expense;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

public interface ExchangeRateProvider {

    /**
     * 返回 1 单位指定币种可兑换的 CNY 金额；数据源不支持的币种不出现在结果中。
     */
    Map<String, BigDecimal> latestToCny(Set<String> currencies);

    String source();
}