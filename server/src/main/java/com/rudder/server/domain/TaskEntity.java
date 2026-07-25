package com.rudder.server.domain;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 任务表 rb_task */
@TableName("rb_task")
@Data
@NoArgsConstructor
public class TaskEntity {

    /** 任务id */
    @TableId("id")
    private Long id;

    /** 工作区id */
    @TableField("workspace_id")
    private Long workspaceId;

    /** 执行 Agent id */
    @TableField("agent_id")
    private Long agentId;

    /** 领取的 Runtime id */
    @TableField("runtime_id")
    private Long runtimeId;

    /** 关联项目id */
    @TableField("project_id")
    private Long projectId;

    /** 触发源：chat / assign / mention / rerun */
    @TableField("trigger_source")
    private String triggerSource;

    /** 任务状态：queued / dispatched / running / completed / failed / cancelled */
    @TableField("status")
    private String status;

    /** 任务提示词 */
    @TableField("prompt")
    private String prompt;

    /** 结果摘要 */
    @TableField("result_summary")
    private String resultSummary;

    /** 失败错误信息 */
    @TableField("error_message")
    private String errorMessage;

    /** Agent 工作目录 */
    @TableField("work_dir")
    private String workDir;

    /** 关联 Issue id */
    @TableField("issue_id")
    private Long issueId;

    /** 关联 Chat 会话 id */
    @TableField("chat_session_id")
    private Long chatSessionId;

    /** 关联 Chat 消息 id */
    @TableField("chat_message_id")
    private Long chatMessageId;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /** 开始执行时间 */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /** 结束时间 */
    @TableField("finished_at")
    private LocalDateTime finishedAt;
}
