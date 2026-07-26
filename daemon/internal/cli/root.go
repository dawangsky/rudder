// Package cli 定义 Rudder 命令行根命令与全局参数（如 Server 地址、profile）。
package cli

import (
	"encoding/json"
	"fmt"

	"github.com/dawangsky/rudder/daemon/internal/config"
	"github.com/dawangsky/rudder/daemon/internal/version"
	"github.com/spf13/cobra"
)

// 全局可配置的 Self-Host Server 地址（不只 localhost）。
var serverBaseURL string

// profileFlag 隔离本机数据目录：desktop 与默认 CLI 可并存。
var profileFlag string

// rootCmd 是所有子命令的父节点。
var rootCmd = &cobra.Command{
	Use:   "rudder",
	Short: "Rudder CLI — 登录、Daemon 与本机 Agent 执行器",
	Long:  "控制面只做协调；本 CLI/Daemon 在用户机器上探测 Agent CLI、领任务并执行。支持 --profile 隔离 Desktop/CLI。",
	PersistentPreRun: func(cmd *cobra.Command, args []string) {
		config.SetProfile(profileFlag)
	},
}

// Execute 启动 Cobra。
func Execute() error {
	return rootCmd.Execute()
}

func init() {
	rootCmd.PersistentFlags().StringVar(
		&serverBaseURL,
		"server",
		"http://127.0.0.1:8080",
		"Rudder Server Base URL（Self-Host 可配）",
	)
	rootCmd.PersistentFlags().StringVar(
		&profileFlag,
		"profile",
		"",
		"本机 profile：空/cli=默认 ~/.rudder；desktop=~/.rudder/profiles/desktop（与 Desktop 隔离）",
	)
	rootCmd.AddCommand(newVersionCmd())
	rootCmd.AddCommand(newLoginCmd())
	rootCmd.AddCommand(newDaemonCmd())
	rootCmd.AddCommand(newRuntimeCmd())
}

func newVersionCmd() *cobra.Command {
	var asJSON bool
	cmd := &cobra.Command{
		Use:   "version",
		Short: "打印 CLI 版本",
		Run: func(cmd *cobra.Command, args []string) {
			if asJSON {
				_ = json.NewEncoder(cmd.OutOrStdout()).Encode(map[string]string{
					"name":    "rudder-cli",
					"version": version.Version,
					"commit":  version.Commit,
					"builtAt": version.BuiltAt,
				})
				return
			}
			fmt.Fprintf(cmd.OutOrStdout(), "rudder-cli %s\n", version.Version)
		},
	}
	cmd.Flags().BoolVar(&asJSON, "json", false, "以 JSON 输出")
	return cmd
}
