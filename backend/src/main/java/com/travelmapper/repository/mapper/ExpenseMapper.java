package com.travelmapper.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travelmapper.repository.entity.Expense;
import lombok.Data;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseMapper extends BaseMapper<Expense> {

    /**
     * 汇总查询：只按分组维度 + 分类 + 币种聚合，避免把明细数据拉回应用层。
     * groupColumns 由服务层白名单生成，不接受外部传入。
     */
    @Select("""
            <script>
            SELECT ANY_VALUE(country_code) AS countryCode,
                   ANY_VALUE(city_code) AS cityCode,
                   ANY_VALUE(trip_id) AS tripId,
                   ANY_VALUE(expense_date) AS expenseDate,
                   category AS category,
                   currency AS currency,
                   SUM(amount_cny) AS totalCny,
                   SUM(amount) AS totalOriginal
            FROM expense
            WHERE user_id = #{userId} AND deleted = 0
            <if test="countryCode != null">AND country_code = #{countryCode}</if>
            <if test="tripId != null">AND trip_id = #{tripId}</if>
            <if test="from != null">AND expense_date &gt;= #{from}</if>
            <if test="to != null">AND expense_date &lt;= #{to}</if>
            GROUP BY
            <foreach collection="groupColumns" item="col" separator=", ">${col}</foreach>,
            category, currency
            ORDER BY 1
            </script>
            """)
    List<SummaryRow> selectSummaryGroups(@Param("userId") Long userId,
                                         @Param("groupColumns") List<String> groupColumns,
                                         @Param("countryCode") String countryCode,
                                         @Param("tripId") Long tripId,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);

    @Select("""
            <script>
            SELECT DISTINCT currency
            FROM expense
            WHERE user_id = #{userId} AND deleted = 0
              AND exchange_rate_snapshot IS NULL
            <if test="countryCode != null">AND country_code = #{countryCode}</if>
            <if test="tripId != null">AND trip_id = #{tripId}</if>
            <if test="from != null">AND expense_date &gt;= #{from}</if>
            <if test="to != null">AND expense_date &lt;= #{to}</if>
            ORDER BY currency
            </script>
            """)
    List<String> selectUnconfiguredCurrencies(@Param("userId") Long userId,
                                              @Param("countryCode") String countryCode,
                                              @Param("tripId") Long tripId,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);

    @Select("""
            SELECT COUNT(*) AS expenseCount,
                   COALESCE(SUM(amount_cny), 0) AS totalCny
            FROM expense
            WHERE user_id = #{userId} AND trip_id = #{tripId} AND deleted = 0
            """)
    TripSummary selectTripSummary(@Param("userId") Long userId, @Param("tripId") Long tripId);

    @Data
    class TripSummary {
        private Long expenseCount;
        private BigDecimal totalCny;
    }

    @Data
    class SummaryRow {
        private String countryCode;
        private String cityCode;
        private Long tripId;
        private LocalDate expenseDate;
        private String category;
        private String currency;
        private BigDecimal totalCny;
        private BigDecimal totalOriginal;
    }
}
