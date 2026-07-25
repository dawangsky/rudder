package com.rudder.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rudder.server.domain.InboxEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InboxMapper extends BaseMapper<InboxEntity> {
}
