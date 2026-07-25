package com.rudder.server.domain;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Agent-Skill 挂载关系表 rb_agent_skill */
@TableName("rb_agent_skill")
@Data
@NoArgsConstructor
public class AgentSkillEntity {

    /** 关系记录id */
    @TableId("id")
    private Long id;

    /** Agent id */
    @TableField("agent_id")
    private Long agentId;

    /** Skill id */
    @TableField("skill_id")
    private Long skillId;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
