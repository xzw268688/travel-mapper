package com.travelmapper.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travelmapper.repository.entity.QuotaPack;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface QuotaPackMapper extends BaseMapper<QuotaPack> {

    @Select("SELECT * FROM quota_pack "
            + "WHERE user_id = #{userId} AND used_count < granted_count AND expire_at > NOW() "
            + "ORDER BY expire_at ASC, id ASC LIMIT 1 FOR UPDATE")
    QuotaPack selectAvailableForUpdate(@Param("userId") Long userId);

    @Update("UPDATE quota_pack SET used_count = used_count + 1 "
            + "WHERE id = #{id} AND used_count < granted_count")
    int incrementUsed(@Param("id") Long id);

    @Update("UPDATE quota_pack SET used_count = used_count - 1 "
            + "WHERE id = #{id} AND used_count > 0")
    int decrementUsed(@Param("id") Long id);
}
