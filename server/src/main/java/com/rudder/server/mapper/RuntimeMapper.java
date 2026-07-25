package com.rudder.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rudder.server.domain.RuntimeEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RuntimeMapper extends BaseMapper<RuntimeEntity> {
}
