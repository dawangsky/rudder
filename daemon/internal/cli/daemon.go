package cli

import (
	"fmt"
	"os"
	"strconv"
	"syscall"
	"time"

	"github.com/dawangsky/rudder/daemon/internal/config"
	"github.com/dawangsky/rudder/daemon/internal/daemon"
	"github.com/spf13/cobra"
)

func newDaemonCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "daemon",
		Short: "管理本机 Daemon（start|stop|status）",
	}
	cmd.AddCommand(&cobra.Command{
		Use:   "start",
		Short: "启动 Daemon：仅为已添加的运行时心跳与领任务",
		RunE: func(cmd *cobra.Command, args []string) error {
			return daemon.Run(serverBaseURL)
		},
	})
	cmd.AddCommand(&cobra.Command{
		Use:   "stop",
		Short: "停止 Daemon",
		RunE: func(cmd *cobra.Command, args []string) error {
			pidPath, err := config.PidPath()
			if err != nil {
				return err
			}
			raw, err := os.ReadFile(pidPath)
			if err != nil {
				fmt.Println("daemon not running")
				return nil
			}
			pid, _ := strconv.Atoi(string(raw))
			if pid <= 0 {
				return fmt.Errorf("invalid pid")
			}
			proc, err := os.FindProcess(pid)
			if err != nil {
				return err
			}
			_ = proc.Signal(syscall.SIGTERM)
			time.Sleep(300 * time.Millisecond)
			_ = os.Remove(pidPath)
			fmt.Println("daemon stop requested")
			return nil
		},
	})
	cmd.AddCommand(&cobra.Command{
		Use:   "status",
		Short: "查看 Daemon 状态",
		RunE: func(cmd *cobra.Command, args []string) error {
			pidPath, err := config.PidPath()
			if err != nil {
				return err
			}
			raw, err := os.ReadFile(pidPath)
			if err != nil {
				fmt.Println("daemon status: not running")
				return nil
			}
			fmt.Printf("daemon status: running pid=%s\n", string(raw))
			return nil
		},
	})
	return cmd
}
