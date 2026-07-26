package com.rudder.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rudder.server.domain.AgentEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentMapper extends BaseMapper<AgentEntity> {

    /** 物理删除（绕过 @TableLogic），用于永久抹去智能体。 */
    @Delete("DELETE FROM rb_agent WHERE id = #{id}")
    int purgeById(@Param("id") Long id);
}
