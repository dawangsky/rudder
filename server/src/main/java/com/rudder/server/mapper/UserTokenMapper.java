package com.rudder.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rudder.server.domain.UserTokenEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserTokenMapper extends BaseMapper<UserTokenEntity> {
}
