package com.travelmapper.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travelmapper.repository.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM `user` WHERE id = #{id}")
    User selectByIdIncludingDeleted(Long id);

    @Select("SELECT * FROM `user` WHERE (username = #{account} OR email = #{account}) LIMIT 1")
    User selectByAccountIncludingDeleted(@Param("account") String account);
}
