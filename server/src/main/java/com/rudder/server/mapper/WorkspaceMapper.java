package com.rudder.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rudder.server.domain.WorkspaceEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkspaceMapper extends BaseMapper<WorkspaceEntity> {
}
