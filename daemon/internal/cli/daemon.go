package cli

import (
	"fmt"

	"github.com/spf13/cobra"
)

// newDaemonCmd 管理本机常驻执行器（关 Desktop 窗口后仍可跑任务）。
func newDaemonCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "daemon",
		Short: "管理本机 Daemon（start|stop|status）",
	}
	cmd.AddCommand(&cobra.Command{
		Use:   "start",
		Short: "启动 Daemon：探测 CLI、注册 Runtime、轮询领任务",
		RunE: func(cmd *cobra.Command, args []string) error {
			fmt.Printf("daemon start stub: server=%s（探测/心跳/领任务后续实现）\n", serverBaseURL)
			return nil
		},
	})
	cmd.AddCommand(&cobra.Command{
		Use:   "stop",
		Short: "停止 Daemon",
		RunE: func(cmd *cobra.Command, args []string) error {
			fmt.Println("daemon stop stub")
			return nil
		},
	})
	cmd.AddCommand(&cobra.Command{
		Use:   "status",
		Short: "查看 Daemon 状态",
		RunE: func(cmd *cobra.Command, args []string) error {
			fmt.Println("daemon status stub: not running")
			return nil
		},
	})
	return cmd
}
