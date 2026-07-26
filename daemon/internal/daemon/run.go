package daemon

import (
	"fmt"
	"os"
	"strconv"
	"time"

	"github.com/dawangsky/rudder/daemon/internal/client"
	"github.com/dawangsky/rudder/daemon/internal/config"
	"github.com/dawangsky/rudder/daemon/internal/detect"
	"github.com/dawangsky/rudder/daemon/internal/execenv"
	"github.com/dawangsky/rudder/daemon/internal/provider"
)

// Run 常驻循环：内置 Provider 按本机安装自动探测注册；stub 等手动项走启用列表；心跳与领任务。
func Run(serverOverride string) error {
	creds, err := config.LoadCredentials()
	if err != nil {
		return fmt.Errorf("请先 rudder login: %w", err)
	}
	base := creds.Server
	if serverOverride != "" {
		base = serverOverride
	}
	api := client.New(base, creds.DaemonToken)
	daemonID, err := config.LoadOrCreateInstanceID()
	if err != nil {
		return err
	}
	host, _ := os.Hostname()
	meta := fmt.Sprintf(`{"profile":%q,"email":%q}`, config.ProfileName(), creds.Email)

	fmt.Printf("daemon profile=%s instance=%s email=%s\n", config.ProfileName(), daemonID, creds.Email)

	runtimeIDs := map[string]string{}
	syncProviders(api, daemonID, host, meta, runtimeIDs)

	if len(runtimeIDs) == 0 {
		fmt.Println("尚未发现可注册运行时。安装 Cursor / Claude Code / Codex 后约 10 秒内自动出现；也可 rudder runtime add --provider stub")
	}

	pidPath, _ := config.PidPath()
	_ = os.WriteFile(pidPath, []byte(strconv.Itoa(os.Getpid())), 0o600)
	defer os.Remove(pidPath)

	fmt.Println("daemon running; poll=3s heartbeat=15s detect=10s; Ctrl+C to stop")
	tickPoll := time.NewTicker(3 * time.Second)
	tickHB := time.NewTicker(15 * time.Second)
	tickSync := time.NewTicker(10 * time.Second)
	defer tickPoll.Stop()
	defer tickHB.Stop()
	defer tickSync.Stop()

	for {
		select {
		case <-tickHB.C:
			for _, id := range runtimeIDs {
				_ = api.Heartbeat(id)
			}
		case <-tickSync.C:
			// 热同步：探测本机已安装内置 Provider + 手动启用列表
			syncProviders(api, daemonID, host, meta, runtimeIDs)
		case <-tickPoll.C:
			for prov, id := range runtimeIDs {
				claim, err := api.Claim(id)
				if err != nil || claim["task"] == nil {
					continue
				}
				go handleClaim(api, prov, claim)
			}
		}
	}
}

// resolveWantProviders 合并：已安装内置（自动）+ enabled 中仍可用的项（stub / 手动）。
func resolveWantProviders() ([]string, error) {
	enabled, err := config.LoadEnabledProviders()
	if err != nil {
		return nil, err
	}
	wantSet := map[string]bool{}
	var want []string

	add := func(p string) {
		if wantSet[p] {
			return
		}
		wantSet[p] = true
		want = append(want, p)
	}

	for _, p := range detect.InstalledBuiltins() {
		add(p)
		// 写回启用列表，便于 CLI list / 删除后再被探测恢复时状态一致
		_ = config.AddEnabledProvider(p)
	}
	for _, p := range enabled {
		if detect.IsBuiltin(p) {
			// 内置以探测为准：未安装则不保留在 want（会从服务端注销）
			continue
		}
		if p == "stub" || detect.IsInstalled(p) {
			add(p)
		}
	}
	return want, nil
}

func syncProviders(api *client.API, daemonID, host, meta string, runtimeIDs map[string]string) {
	wantList, err := resolveWantProviders()
	if err != nil {
		return
	}
	want := map[string]bool{}
	for _, p := range wantList {
		want[p] = true
		if _, ok := runtimeIDs[p]; ok {
			continue
		}
		if err := detect.RequireInstalled(p); err != nil {
			fmt.Printf("skip provider=%s: %v\n", p, err)
			continue
		}
		rt, err := api.RegisterRuntime(daemonID, p, host, meta)
		if err != nil {
			fmt.Printf("register runtime %s failed: %v\n", p, err)
			continue
		}
		runtimeIDs[p] = fmt.Sprint(rt["id"])
		fmt.Printf("registered runtime provider=%s id=%s\n", p, runtimeIDs[p])
	}
	for p := range runtimeIDs {
		if !want[p] {
			_ = api.DeleteRuntimeByProvider(daemonID, p)
			delete(runtimeIDs, p)
			fmt.Printf("unregistered runtime provider=%s\n", p)
		}
	}
}

func handleClaim(api *client.API, providerName string, claim map[string]any) {
	task, _ := claim["task"].(map[string]any)
	agent, _ := claim["agent"].(map[string]any)
	if task == nil || agent == nil {
		return
	}
	taskID := fmt.Sprint(task["id"])
	workDir := fmt.Sprint(claim["workDir"])
	envRoot := fmt.Sprint(claim["envRoot"])
	localMode, _ := claim["localPathMode"].(bool)
	skills, _ := claim["skills"].([]any)
	var skillMaps []map[string]any
	for _, s := range skills {
		if m, ok := s.(map[string]any); ok {
			skillMaps = append(skillMaps, m)
		}
	}
	instructions := fmt.Sprint(agent["instructions"])
	prompt := fmt.Sprint(task["prompt"])
	agentProvider := fmt.Sprint(agent["provider"])
	if agentProvider == "" {
		agentProvider = providerName
	}

	unlock := func() {}
	if localMode {
		unlock = execenv.LockPath(workDir)
	}
	defer unlock()

	_ = api.Report(taskID, map[string]any{"status": "running"})
	if err := execenv.Prepare(envRoot, workDir, localMode, skillMaps, instructions); err != nil {
		_ = api.Report(taskID, map[string]any{"status": "failed", "errorMessage": err.Error()})
		return
	}
	execenv.AppendLog(envRoot, "start provider="+agentProvider)
	runProvider := agentProvider
	res := provider.Run(runProvider, workDir, prompt, instructions)
	if res.Err != nil && runProvider != "stub" {
		execenv.AppendLog(envRoot, "provider failed, fallback stub: "+res.Err.Error())
		_ = api.Report(taskID, map[string]any{"status": "log", "line": "fallback to stub provider"})
		res = provider.Run("stub", workDir, prompt, instructions)
	}
	_ = api.Report(taskID, map[string]any{"status": "log", "line": "provider finished"})
	if res.Err != nil {
		_ = api.Report(taskID, map[string]any{"status": "failed", "errorMessage": res.Err.Error()})
		return
	}
	_ = api.Report(taskID, map[string]any{"status": "completed", "resultSummary": res.Summary})
}
