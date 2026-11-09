package com.travelmapper.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.travelmapper.repository.enums.CardStatus;
import com.travelmapper.repository.enums.CardType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("card_key")
public class CardKey {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchId;
    private String code;
    private CardType type;
    private Integer value;
    private BigDecimal priceCny;
    private CardStatus status;
    private LocalDateTime expireAt;
    private Long usedBy;
    private LocalDateTime usedAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
