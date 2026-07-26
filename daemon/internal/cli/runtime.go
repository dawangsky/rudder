package cli

import (
	"encoding/json"
	"fmt"
	"os"
	"strings"

	"github.com/dawangsky/rudder/daemon/internal/client"
	"github.com/dawangsky/rudder/daemon/internal/config"
	"github.com/dawangsky/rudder/daemon/internal/detect"
	"github.com/spf13/cobra"
)

func newRuntimeCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "runtime",
		Short: "内置探测 + 自定义命令运行时（名称/命令必填并校验）",
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
			daemonID, err := config.LoadOrCreateInstanceID()
			if err != nil {
				return err
			}
			host, _ := os.Hostname()
			meta := fmt.Sprintf(`{"profile":%q}`, config.ProfileName())
			rt, err := api.RegisterRuntime(daemonID, addProvider, host, meta)
			if err != nil {
				return fmt.Errorf("注册失败: %w", err)
			}
			if err := config.AddEnabledProvider(addProvider); err != nil {
				return err
			}
			fmt.Printf("已添加运行时 provider=%s id=%s profile=%s\n", addProvider, rt["id"], config.ProfileName())
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
			if detect.IsCustomProviderKey(removeProvider) {
				_ = config.RemoveCustomRuntime(removeProvider)
			} else if err := config.RemoveEnabledProvider(removeProvider); err != nil {
				return err
			}
			api, err := daemonAPI()
			if err != nil {
				return fmt.Errorf("本机列表已清除，但删除服务端记录失败（请先 login）: %w", err)
			}
			daemonID, err := config.LoadOrCreateInstanceID()
			if err != nil {
				return fmt.Errorf("本机列表已清除，但读取实例 ID 失败: %w", err)
			}
			if err := api.DeleteRuntimeByProvider(daemonID, removeProvider); err != nil {
				return fmt.Errorf("本机列表已清除，但删除服务端记录失败: %w", err)
			}
			fmt.Printf("已移除运行时 provider=%s profile=%s\n", removeProvider, config.ProfileName())
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
			customs, err := config.LoadCustomRuntimes()
			if err != nil {
				return err
			}
			if len(enabled) == 0 && len(customs) == 0 {
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
			for _, c := range customs {
				status := "ok"
				if err := detect.ValidateCommand(c.Command); err != nil {
					status = "INVALID: " + err.Error()
				}
				fmt.Printf("- %s\t%s\t%s\t%q\n", c.ProviderKey, c.Name, status, c.Command)
			}
			return nil
		},
	}

	var (
		customBase string
		customName string
		customCmd  string
		customDesc string
	)
	addCustom := &cobra.Command{
		Use:          "add-custom",
		Short:        "添加自定义运行时：须指定基础协议、显示名称与启动命令，并校验命令有效",
		SilenceUsage: true,
		RunE: func(cmd *cobra.Command, args []string) error {
			customBase = strings.TrimSpace(customBase)
			customName = strings.TrimSpace(customName)
			customCmd = strings.TrimSpace(customCmd)
			if customBase == "" || !detect.IsBuiltin(customBase) {
				return fmt.Errorf("--base 须为 cursor / claude_code / codex")
			}
			if customName == "" {
				return fmt.Errorf("--name 不能为空")
			}
			if customCmd == "" {
				return fmt.Errorf("--command 不能为空")
			}
			if err := detect.ValidateCommand(customCmd); err != nil {
				return err
			}
			key := config.MakeCustomProviderKey(customBase, customName, customCmd)
			item := config.CustomRuntime{
				ProviderKey:  key,
				BaseProvider: customBase,
				Name:         customName,
				Command:      customCmd,
				Description:  strings.TrimSpace(customDesc),
			}
			if err := config.AddCustomRuntime(item); err != nil {
				return err
			}
			api, err := daemonAPI()
			if err != nil {
				return err
			}
			daemonID, err := config.LoadOrCreateInstanceID()
			if err != nil {
				return err
			}
			host, _ := os.Hostname()
			metaMap := map[string]any{
				"profile":      config.ProfileName(),
				"kind":         "custom",
				"baseProvider": customBase,
				"displayName":  customName,
				"command":      customCmd,
				"description":  item.Description,
			}
			metaBytes, _ := json.Marshal(metaMap)
			rt, err := api.RegisterRuntime(daemonID, key, host, string(metaBytes))
			if err != nil {
				return fmt.Errorf("注册失败: %w", err)
			}
			fmt.Printf("ok provider=%s id=%s name=%s\n", key, rt["id"], customName)
			return nil
		},
	}
	addCustom.Flags().StringVar(&customBase, "base", "", "基础协议：cursor | claude_code | codex")
	addCustom.Flags().StringVar(&customName, "name", "", "显示名称（必填）")
	addCustom.Flags().StringVar(&customCmd, "command", "", "启动命令（必填，须本机可执行）")
	addCustom.Flags().StringVar(&customDesc, "description", "", "描述（可选）")
	_ = addCustom.MarkFlagRequired("base")
	_ = addCustom.MarkFlagRequired("name")
	_ = addCustom.MarkFlagRequired("command")

	var validateCmdLine string
	validateCmd := &cobra.Command{
		Use:          "validate-command",
		Short:        "仅校验命令是否可在本机找到（不注册）",
		SilenceUsage: true,
		RunE: func(cmd *cobra.Command, args []string) error {
			if err := detect.ValidateCommand(validateCmdLine); err != nil {
				return err
			}
			fmt.Println("ok command valid")
			return nil
		},
	}
	validateCmd.Flags().StringVar(&validateCmdLine, "command", "", "要校验的命令行")
	_ = validateCmd.MarkFlagRequired("command")

	var detectProvider string
	detectCmd := &cobra.Command{
		Use:          "detect",
		Short:        "仅探测本机是否安装 Provider（不注册）",
		SilenceUsage: true,
		RunE: func(cmd *cobra.Command, args []string) error {
			if detectProvider == "" {
				return fmt.Errorf("请指定 --provider")
			}
			if err := detect.RequireInstalled(detectProvider); err != nil {
				return err
			}
			fmt.Printf("ok provider=%s installed\n", detectProvider)
			return nil
		},
	}
	detectCmd.Flags().StringVar(&detectProvider, "provider", "", "cursor | claude_code | codex | stub")
	_ = detectCmd.MarkFlagRequired("provider")

	var enableProvider string
	enableCmd := &cobra.Command{
		Use:          "enable",
		Short:        "写入本机启用列表（供 Daemon 心跳），不访问 Server",
		SilenceUsage: true,
		RunE: func(cmd *cobra.Command, args []string) error {
			if enableProvider == "" {
				return fmt.Errorf("请指定 --provider")
			}
			if err := detect.RequireInstalled(enableProvider); err != nil {
				return err
			}
			if err := config.AddEnabledProvider(enableProvider); err != nil {
				return err
			}
			fmt.Printf("已启用本机运行时 provider=%s\n", enableProvider)
			return nil
		},
	}
	enableCmd.Flags().StringVar(&enableProvider, "provider", "", "cursor | claude_code | codex | stub")
	_ = enableCmd.MarkFlagRequired("provider")

	cmd.AddCommand(add, remove, list, detectCmd, enableCmd, addCustom, validateCmd)
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
