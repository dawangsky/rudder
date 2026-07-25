// Package cli 定义 Rudder 命令行根命令与全局参数（如 Server 地址）。
package cli

import (
	"fmt"

	"github.com/spf13/cobra"
)

// 全局可配置的 Self-Host Server 地址（不只 localhost）。
var serverBaseURL string

// rootCmd 是所有子命令的父节点。
var rootCmd = &cobra.Command{
	Use:   "rudder",
	Short: "Rudder CLI — 登录、Daemon 与本机 Agent 执行器",
	Long:  "控制面只做协调；本 CLI/Daemon 在用户机器上探测 Agent CLI、领任务并执行。",
}

// Execute 启动 Cobra。
func Execute() error {
	return rootCmd.Execute()
}

func init() {
	// --server 可指向任意 Self-Host 地址
	rootCmd.PersistentFlags().StringVar(
		&serverBaseURL,
		"server",
		"http://127.0.0.1:8080",
		"Rudder Server Base URL（Self-Host 可配）",
	)
	rootCmd.AddCommand(newVersionCmd())
	rootCmd.AddCommand(newLoginCmd())
	rootCmd.AddCommand(newDaemonCmd())
	rootCmd.AddCommand(newRuntimeCmd())
}

func newVersionCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "version",
		Short: "打印 CLI 版本",
		Run: func(cmd *cobra.Command, args []string) {
			fmt.Println("rudder-cli 0.1.0-dev")
		},
	}
}
