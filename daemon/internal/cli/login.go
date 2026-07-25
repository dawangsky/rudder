package cli

import (
	"fmt"

	"github.com/spf13/cobra"
)

// newLoginCmd 登录控制面并保存 Daemon Token（实现见后续任务）。
func newLoginCmd() *cobra.Command {
	var email string
	cmd := &cobra.Command{
		Use:   "login",
		Short: "登录 Rudder Server，保存本机 Daemon 凭证",
		RunE: func(cmd *cobra.Command, args []string) error {
			// 骨架：后续对接 /api/auth 与本地配置写入
			if email == "" {
				return fmt.Errorf("请使用 --email 指定邮箱（密码后续交互或 --password）")
			}
			fmt.Printf("login stub: server=%s email=%s（鉴权 API 尚未接通）\n", serverBaseURL, email)
			return nil
		},
	}
	cmd.Flags().StringVar(&email, "email", "", "登录邮箱")
	return cmd
}
