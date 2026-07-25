package com.rudder.server.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 工作区表 rb_workspace。 */
@Data
@TableName("rb_workspace")
public class WorkspaceEntity {
    @TableId
    private Long id;
    private String name;
    private String slug;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
