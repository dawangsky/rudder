package com.rudder.server.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** Agent-Skill 挂载关系表 rb_agent_skill */
@TableName("rb_agent_skill")
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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public Long getSkillId() { return skillId; }
    public void setSkillId(Long skillId) { this.skillId = skillId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
