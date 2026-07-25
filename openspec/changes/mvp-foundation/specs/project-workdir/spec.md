## ADDED Requirements

### Requirement: 项目可配置本地路径
系统 SHALL 支持 Project 对象，并可配置本机绝对路径作为执行工作目录。

#### Scenario: 保存项目路径
- **WHEN** 用户为项目设置存在且可读写的本机路径
- **THEN** 系统保存该路径并与当前 Daemon 机器关联校验规则生效

### Requirement: 工作目录优先级
解析 Task 工作目录时，系统 SHALL 优先使用上下文所属 Project 的本地路径；否则使用默认沙箱路径 `{RUDDER_WORKSPACES_ROOT|~/rudder_workspaces}/{workspace_id}/{task_id}/workdir/`。

#### Scenario: Chat 无项目
- **WHEN** Chat 未关联带本地路径的 Project
- **THEN** Daemon 在默认沙箱 workdir 中执行 Agent

#### Scenario: Chat 关联项目路径
- **WHEN** Chat 或 Issue 归属已配置本地路径的 Project
- **THEN** Daemon 以该路径为 Agent cwd（项目优先）

### Requirement: 本地路径串行与安全
对同一真实本地路径，Daemon SHALL 串行执行任务；不得删除用户项目目录；output/logs 等产物可放在沙箱 env 根下。

#### Scenario: 同路径第二任务
- **WHEN** 已有任务占用某本地路径
- **THEN** 后续同路径任务等待或进入可观察的等待状态且可取消

#### Scenario: 危险路径拒绝
- **WHEN** 用户配置的路径为系统根或拒绝列表中的路径
- **THEN** 系统拒绝保存或拒绝执行
