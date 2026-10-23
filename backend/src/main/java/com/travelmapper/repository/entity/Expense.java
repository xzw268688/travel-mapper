package com.travelmapper.repository.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.travelmapper.repository.enums.ExpenseCategory;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("expense")
public class Expense {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long tripId;
    private String countryCode;
    private String cityCode;
    private String currency;
    private BigDecimal amount;
    private BigDecimal exchangeRateSnapshot;

    /**
     * Database-generated STORED column. Never insert or update from the application.
     */
    @TableField(value = "amount_cny", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private BigDecimal amountCny;

    private ExpenseCategory category;
    private LocalDate expenseDate;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
