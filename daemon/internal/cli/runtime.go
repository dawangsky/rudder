package cli

import (
	"fmt"
	"os"

	"github.com/dawangsky/rudder/daemon/internal/client"
	"github.com/dawangsky/rudder/daemon/internal/config"
	"github.com/dawangsky/rudder/daemon/internal/detect"
	"github.com/google/uuid"
	"github.com/spf13/cobra"
)

func newRuntimeCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "runtime",
		Short: "手动添加/移除本机运行时（须先探测已安装再注册）",
	}

	var addProvider string
	add := &cobra.Command{
		Use:          "add",
		Short:        "添加运行时：本机未安装对应 CLI 则注册失败",
		SilenceUsage: true,
		RunE: func(cmd *cobra.Command, args []string) error {
			if addProvider == "" {
				return fmt.Errorf("请指定 --provider（%v）", detect.AllowedProviders())
			}
			if err := detect.RequireInstalled(addProvider); err != nil {
				return err
			}
			api, err := daemonAPI()
			if err != nil {
				return err
			}
			daemonID := uuid.NewString()
			host, _ := os.Hostname()
			rt, err := api.RegisterRuntime(daemonID, addProvider, host)
			if err != nil {
				return fmt.Errorf("注册失败: %w", err)
			}
			if err := config.AddEnabledProvider(addProvider); err != nil {
				return err
			}
			fmt.Printf("已添加运行时 provider=%s id=%s\n", addProvider, rt["id"])
			fmt.Println("若 Daemon 已在运行，约 10 秒内会自动接管心跳；否则请 rudder daemon start")
			return nil
		},
	}
	add.Flags().StringVar(&addProvider, "provider", "", "cursor | claude_code | codex | stub")
	_ = add.MarkFlagRequired("provider")

	var removeProvider string
	remove := &cobra.Command{
		Use:          "remove",
		Short:        "移除运行时：清本机启用列表并删除服务端记录",
		SilenceUsage: true,
		RunE: func(cmd *cobra.Command, args []string) error {
			if removeProvider == "" {
				return fmt.Errorf("请指定 --provider")
			}
			if err := config.RemoveEnabledProvider(removeProvider); err != nil {
				return err
			}
			api, err := daemonAPI()
			if err != nil {
				return fmt.Errorf("本机列表已清除，但删除服务端记录失败（请先 login）: %w", err)
			}
			if err := api.DeleteRuntimeByProvider(removeProvider); err != nil {
				return fmt.Errorf("本机列表已清除，但删除服务端记录失败: %w", err)
			}
			fmt.Printf("已移除运行时 provider=%s\n", removeProvider)
			return nil
		},
	}
	remove.Flags().StringVar(&removeProvider, "provider", "", "要移除的 provider")
	_ = remove.MarkFlagRequired("provider")

	list := &cobra.Command{
		Use:   "list",
		Short: "列出本机已添加的运行时与探测结果",
		RunE: func(cmd *cobra.Command, args []string) error {
			enabled, err := config.LoadEnabledProviders()
			if err != nil {
				return err
			}
			if len(enabled) == 0 {
				fmt.Println("(空) 尚未添加运行时")
				return nil
			}
			for _, p := range enabled {
				status := "installed"
				if err := detect.RequireInstalled(p); err != nil {
					status = "MISSING: " + err.Error()
				}
				fmt.Printf("- %s\t%s\n", p, status)
			}
			return nil
		},
	}

	cmd.AddCommand(add, remove, list)
	return cmd
}

func daemonAPI() (*client.API, error) {
	creds, err := config.LoadCredentials()
	if err != nil {
		return nil, fmt.Errorf("请先 rudder login: %w", err)
	}
	base := creds.Server
	if serverBaseURL != "" {
		base = serverBaseURL
	}
	return client.New(base, creds.DaemonToken), nil
}
