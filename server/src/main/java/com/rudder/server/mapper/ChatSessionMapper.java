package com.rudder.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rudder.server.domain.ChatSessionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSessionEntity> {
}
