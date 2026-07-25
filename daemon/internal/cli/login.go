package cli

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"github.com/dawangsky/rudder/daemon/internal/config"
	"github.com/spf13/cobra"
)

// newLoginCmd 调用 Server /api/auth/daemon-login，保存 Daemon Token（与 session 分离）。
func newLoginCmd() *cobra.Command {
	var email string
	var password string
	cmd := &cobra.Command{
		Use:   "login",
		Short: "登录 Rudder Server，保存当前 profile 的 Daemon 凭证",
		RunE: func(cmd *cobra.Command, args []string) error {
			if email == "" || password == "" {
				return fmt.Errorf("请提供 --email 与 --password")
			}
			payload := map[string]string{"email": email, "password": password}
			body, _ := json.Marshal(payload)
			url := serverBaseURL + "/api/auth/daemon-login"
			req, err := http.NewRequest(http.MethodPost, url, bytes.NewReader(body))
			if err != nil {
				return err
			}
			req.Header.Set("Content-Type", "application/json")
			client := &http.Client{Timeout: 15 * time.Second}
			resp, err := client.Do(req)
			if err != nil {
				return err
			}
			defer resp.Body.Close()
			var result map[string]any
			if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
				return err
			}
			if resp.StatusCode >= 300 {
				msg, _ := result["message"].(string)
				if msg == "" {
					msg = resp.Status
				}
				return fmt.Errorf("login failed: %s", msg)
			}
			token, _ := result["daemonToken"].(string)
			if token == "" {
				return fmt.Errorf("响应中缺少 daemonToken")
			}
			path, err := config.SaveCredentials(&config.Credentials{
				Server:      serverBaseURL,
				Email:       email,
				DaemonToken: token,
			})
			if err != nil {
				return err
			}
			fmt.Printf("登录成功（profile=%s），Daemon Token 已写入 %s\n", config.ProfileName(), path)
			return nil
		},
	}
	cmd.Flags().StringVar(&email, "email", "", "登录邮箱")
	cmd.Flags().StringVar(&password, "password", "", "登录密码")
	return cmd
}
