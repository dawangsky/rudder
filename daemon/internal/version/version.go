// Package version 提供 CLI/Daemon 版本信息。
// 发布前更新 ../VERSION；构建脚本用 -ldflags 注入，保证与文件一致。
package version

// Version 与 daemon/VERSION 对齐；未走 ldflags 时的兜底。
var Version = "0.2.0"

// Commit / BuiltAt 可选构建元数据。
var (
	Commit  = "dev"
	BuiltAt = "unknown"
)
