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
	"github.com/google/uuid"
)

// Run 常驻循环：注册 Runtime、心跳、领任务、执行、上报。
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
	daemonID := uuid.NewString()
	host, _ := os.Hostname()
	providers := detect.DetectProviders()
	runtimeIDs := map[string]string{}
	for _, p := range providers {
		rt, err := api.RegisterRuntime(daemonID, p, host)
		if err != nil {
			return fmt.Errorf("register runtime %s: %w", p, err)
		}
		runtimeIDs[p] = fmt.Sprint(rt["id"])
		fmt.Printf("registered runtime provider=%s id=%s\n", p, runtimeIDs[p])
	}

	pidPath, _ := config.PidPath()
	_ = os.WriteFile(pidPath, []byte(strconv.Itoa(os.Getpid())), 0o600)
	defer os.Remove(pidPath)

	fmt.Println("daemon running; poll=3s heartbeat=15s; Ctrl+C to stop")
	tickPoll := time.NewTicker(3 * time.Second)
	tickHB := time.NewTicker(15 * time.Second)
	defer tickPoll.Stop()
	defer tickHB.Stop()

	for {
		select {
		case <-tickHB.C:
			for _, id := range runtimeIDs {
				_ = api.Heartbeat(id)
			}
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
