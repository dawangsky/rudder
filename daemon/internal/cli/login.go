package cli

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"time"

	"github.com/spf13/cobra"
)

// newLoginCmd 调用 Server /api/auth/daemon-login，保存 Daemon Token（与 session 分离）。
func newLoginCmd() *cobra.Command {
	var email string
	var password string
	cmd := &cobra.Command{
		Use:   "login",
		Short: "登录 Rudder Server，保存本机 Daemon 凭证",
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
			path, err := saveDaemonConfig(serverBaseURL, email, token)
			if err != nil {
				return err
			}
			fmt.Printf("登录成功，Daemon Token 已写入 %s\n", path)
			return nil
		},
	}
	cmd.Flags().StringVar(&email, "email", "", "登录邮箱")
	cmd.Flags().StringVar(&password, "password", "", "登录密码")
	return cmd
}

// saveDaemonConfig 将凭证写到 ~/.rudder/credentials.json（不进 Git）。
func saveDaemonConfig(server, email, daemonToken string) (string, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}
	dir := filepath.Join(home, ".rudder")
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return "", err
	}
	path := filepath.Join(dir, "credentials.json")
	data := map[string]string{
		"server":      server,
		"email":       email,
		"daemonToken": daemonToken,
	}
	raw, _ := json.MarshalIndent(data, "", "  ")
	if err := os.WriteFile(path, raw, 0o600); err != nil {
		return "", err
	}
	return path, nil
}
