package daemon

import (
	"encoding/json"
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/dawangsky/rudder/daemon/internal/client"
	"github.com/dawangsky/rudder/daemon/internal/config"
	"github.com/dawangsky/rudder/daemon/internal/detect"
	"github.com/dawangsky/rudder/daemon/internal/execenv"
	"github.com/dawangsky/rudder/daemon/internal/provider"
	"github.com/dawangsky/rudder/daemon/internal/skills"
)

// Run 常驻循环：内置自动探测 + 自定义命令运行时心跳领任务。
func Run(serverOverride string) error {
	detect.EnsureUserPath()
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

	fmt.Printf("daemon profile=%s instance=%s email=%s\n", config.ProfileName(), daemonID, creds.Email)

	runtimeIDs := map[string]string{}
	customCmds := map[string]string{}
	syncProviders(api, daemonID, host, creds.Email, runtimeIDs, customCmds)

	if len(runtimeIDs) == 0 {
		fmt.Println("尚未发现可注册运行时。安装 OpenCode / Claude Code / Qwen / CodeBuddy 等后约 10 秒内自动出现；也可添加自定义运行时")
	}

	pidPath, _ := config.PidPath()
	_ = os.WriteFile(pidPath, []byte(strconv.Itoa(os.Getpid())), 0o600)
	defer os.Remove(pidPath)

	fmt.Println("daemon running; poll=3s heartbeat=15s detect=10s; Ctrl+C to stop")
	tickPoll := time.NewTicker(3 * time.Second)
	tickHB := time.NewTicker(15 * time.Second)
	tickSync := time.NewTicker(10 * time.Second)
	tickSkills := time.NewTicker(30 * time.Second)
	defer tickPoll.Stop()
	defer tickHB.Stop()
	defer tickSync.Stop()
	defer tickSkills.Stop()

	reportLocalSkills(api, daemonID, runtimeIDs)

	for {
		select {
		case <-tickHB.C:
			for _, id := range runtimeIDs {
				_ = api.Heartbeat(id)
			}
		case <-tickSync.C:
			syncProviders(api, daemonID, host, creds.Email, runtimeIDs, customCmds)
		case <-tickSkills.C:
			reportLocalSkills(api, daemonID, runtimeIDs)
		case <-tickPoll.C:
			for prov, id := range runtimeIDs {
				claim, err := api.Claim(id)
				if err != nil || claim["task"] == nil {
					continue
				}
				go handleClaim(api, prov, claim, customCmds[prov])
			}
		}
	}
}

func reportLocalSkills(api *client.API, daemonID string, runtimeIDs map[string]string) {
	if len(runtimeIDs) == 0 {
		return
	}
	found := skills.ScanLocal()
	payload := make([]map[string]any, 0, len(found))
	for _, s := range found {
		payload = append(payload, map[string]any{
			"name":        s.Name,
			"description": s.Description,
			"content":     s.Content,
			"sourcePath":  s.SourcePath,
			"contentHash": s.ContentHash,
		})
	}
	for _, id := range runtimeIDs {
		if err := api.ReportSkills(daemonID, id, payload); err != nil {
			fmt.Printf("report skills runtime=%s failed: %v\n", id, err)
		}
	}
}

func syncProviders(
	api *client.API,
	daemonID, host, email string,
	runtimeIDs map[string]string,
	customCmds map[string]string,
) {
	if remote, err := api.ListProtocols(); err == nil {
		// 成功拉取则严格按工作区启用目录探测（即使为空也不回退全量种子）
		specs := make([]detect.ProviderSpec, 0, len(remote))
		for _, r := range remote {
			code := strings.TrimSpace(r.Code)
			if code == "" {
				continue
			}
			specs = append(specs, detect.ProviderSpec{ID: code, Bins: r.Bins})
		}
		detect.ApplyRemoteCatalog(specs)
	}

	wantMeta := map[string]string{}

	for _, p := range detect.InstalledBuiltins() {
		_ = config.AddEnabledProvider(p)
		meta, _ := json.Marshal(map[string]any{
			"profile": config.ProfileName(),
			"email":   email,
			"kind":    "builtin",
		})
		wantMeta[p] = string(meta)
	}
	enabled, _ := config.LoadEnabledProviders()
	for _, p := range enabled {
		if detect.IsBuiltin(p) {
			continue
		}
		if p == "stub" {
			meta, _ := json.Marshal(map[string]any{
				"profile": config.ProfileName(),
				"email":   email,
				"kind":    "stub",
			})
			wantMeta[p] = string(meta)
			continue
		}
		// 本地曾启用、但工作区已停用的协议：从本机列表移除且不再注册
		_ = config.RemoveEnabledProvider(p)
	}
	customs, _ := config.LoadCustomRuntimes()
	for _, c := range customs {
		base := c.BaseProvider
		if base == "" {
			base = detect.BaseProvider(c.ProviderKey)
		}
		if !detect.IsBuiltin(base) && base != "stub" {
			fmt.Printf("skip custom=%s: base protocol %s disabled or unknown\n", c.ProviderKey, base)
			continue
		}
		if err := detect.ValidateCommand(c.Command); err != nil {
			fmt.Printf("skip custom=%s: %v\n", c.ProviderKey, err)
			continue
		}
		meta, _ := json.Marshal(map[string]any{
			"profile":      config.ProfileName(),
			"email":        email,
			"kind":         "custom",
			"baseProvider": c.BaseProvider,
			"displayName":  c.Name,
			"command":      c.Command,
			"description":  c.Description,
		})
		wantMeta[c.ProviderKey] = string(meta)
		customCmds[c.ProviderKey] = c.Command
	}

	// 每次 sync 都 upsert：UI 删掉服务端记录后，内存里旧 runtimeId 不能挡住重新注册
	for p, meta := range wantMeta {
		if !detect.IsCustomProviderKey(p) {
			if err := detect.RequireInstalled(p); err != nil {
				fmt.Printf("skip provider=%s: %v\n", p, err)
				continue
			}
		}
		rt, err := api.RegisterRuntime(daemonID, p, host, meta)
		if err != nil {
			fmt.Printf("register runtime %s failed: %v\n", p, err)
			continue
		}
		id := fmt.Sprint(rt["id"])
		if prev, ok := runtimeIDs[p]; !ok || prev != id {
			fmt.Printf("registered runtime provider=%s id=%s\n", p, id)
		}
		runtimeIDs[p] = id
	}
	for p := range runtimeIDs {
		if _, ok := wantMeta[p]; !ok {
			_ = api.DeleteRuntimeByProvider(daemonID, p)
			delete(runtimeIDs, p)
			delete(customCmds, p)
			fmt.Printf("unregistered runtime provider=%s\n", p)
		}
	}
}

func handleClaim(api *client.API, providerName string, claim map[string]any, customCommand string) {
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
	model := fmt.Sprint(agent["model"])
	if model == "" || model == "<nil>" {
		model = "default"
	}
	thinkingMode := fmt.Sprint(agent["thinkingMode"])
	if thinkingMode == "" || thinkingMode == "<nil>" {
		thinkingMode = "cli"
	}
	prompt := fmt.Sprint(task["prompt"])
	agentProvider := fmt.Sprint(agent["provider"])
	if agentProvider == "" {
		agentProvider = providerName
	}
	if cmd, ok := claim["command"].(string); ok && cmd != "" {
		customCommand = cmd
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
	execenv.AppendLog(envRoot, "start provider="+agentProvider+" model="+model+" thinking="+thinkingMode)

	var res provider.Result
	if customCommand != "" {
		res = provider.RunCommandLine(customCommand, workDir, prompt, instructions, model, thinkingMode)
	} else {
		runProvider := detect.BaseProvider(agentProvider)
		res = provider.Run(runProvider, workDir, prompt, instructions, model, thinkingMode)
		if res.Err != nil && runProvider != "stub" {
			execenv.AppendLog(envRoot, "provider failed, fallback stub: "+res.Err.Error())
			_ = api.Report(taskID, map[string]any{"status": "log", "line": "fallback to stub provider"})
			res = provider.Run("stub", workDir, prompt, instructions, model, thinkingMode)
		}
	}
	_ = api.Report(taskID, map[string]any{"status": "log", "line": "provider finished"})
	if res.Err != nil {
		_ = api.Report(taskID, map[string]any{"status": "failed", "errorMessage": res.Err.Error()})
		return
	}
	_ = api.Report(taskID, map[string]any{"status": "completed", "resultSummary": res.Summary})
}
