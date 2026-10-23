package com.travelmapper.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travelmapper.repository.entity.CardKey;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface CardKeyMapper extends BaseMapper<CardKey> {

    @Update("UPDATE card_key SET status = 'USED', used_by = #{userId}, used_at = NOW() "
            + "WHERE id = #{id} AND status = 'UNUSED' "
            + "AND (expire_at IS NULL OR expire_at > NOW())")
    int markUsed(@Param("id") Long id, @Param("userId") Long userId);
}
