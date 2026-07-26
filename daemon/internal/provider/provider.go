package provider

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"
)

type Result struct {
	Summary string
	Err     error
}

// RunCommandLine 执行自定义启动命令；支持 {prompt} 占位，否则将 prompt 作为末尾参数追加。
func RunCommandLine(cmdline, workDir, prompt, instructions, model, thinkingMode string) Result {
	cmdline = strings.TrimSpace(cmdline)
	if cmdline == "" {
		return Result{Err: fmt.Errorf("自定义命令为空")}
	}
	expanded := cmdline
	if strings.Contains(cmdline, "{prompt}") {
		expanded = strings.ReplaceAll(cmdline, "{prompt}", shellQuote(prompt))
	} else {
		expanded = cmdline + " " + shellQuote(prompt)
	}
	cmd := exec.Command("sh", "-c", expanded)
	cmd.Dir = workDir
	cmd.Env = append(os.Environ(),
		"RUDDER_PROMPT="+prompt,
		"RUDDER_INSTRUCTIONS="+instructions,
		"RUDDER_MODEL="+model,
		"RUDDER_THINKING_MODE="+thinkingMode,
	)
	out, err := cmd.CombinedOutput()
	summary := string(out)
	if len(summary) > 4000 {
		summary = summary[:4000] + "…"
	}
	if err != nil {
		return Result{Summary: summary, Err: fmt.Errorf("%v: %s", err, summary)}
	}
	return Result{Summary: strings.TrimSpace(summary), Err: nil}
}

func shellQuote(s string) string {
	if s == "" {
		return "''"
	}
	return "'" + strings.ReplaceAll(s, "'", `'"'"'`) + "'"
}

// Run 按 provider 执行；找不到真实 CLI 时回退 stub，保证端到端可冒烟。
func Run(provider, workDir, prompt, instructions, model, thinkingMode string) Result {
	env := map[string]string{
		"RUDDER_PROMPT":         prompt,
		"RUDDER_INSTRUCTIONS":   instructions,
		"RUDDER_MODEL":          model,
		"RUDDER_THINKING_MODE":  thinkingMode,
	}
	switch provider {
	case "claude_code":
		if bin, err := exec.LookPath("claude"); err == nil {
			return runCmd(bin, workDir, []string{"-p", prompt}, env)
		}
	case "codex":
		if bin, err := exec.LookPath("codex"); err == nil {
			return runCmd(bin, workDir, []string{"exec", prompt}, env)
		}
	case "cursor":
		for _, b := range []string{"cursor-agent", "agent", "cursor"} {
			if bin, err := exec.LookPath(b); err == nil {
				return runCmd(bin, workDir, []string{prompt}, env)
			}
		}
	}
	return runStub(workDir, provider, prompt, instructions)
}

func runStub(workDir, provider, prompt, instructions string) Result {
	out := fmt.Sprintf("Rudder stub provider (%s) finished at %s\n\nInstructions:\n%s\n\nPrompt:\n%s\n",
		provider, time.Now().Format(time.RFC3339), instructions, prompt)
	_ = os.WriteFile(filepath.Join(workDir, "RUDDER_STUB_RESULT.md"), []byte(out), 0o644)
	return Result{Summary: strings.TrimSpace(out), Err: nil}
}

func runCmd(bin, workDir string, args []string, env map[string]string) Result {
	cmd := exec.Command(bin, args...)
	cmd.Dir = workDir
	cmd.Env = os.Environ()
	for k, v := range env {
		cmd.Env = append(cmd.Env, k+"="+v)
	}
	out, err := cmd.CombinedOutput()
	summary := string(out)
	if len(summary) > 4000 {
		summary = summary[:4000] + "…"
	}
	if err != nil {
		return Result{Summary: summary, Err: fmt.Errorf("%v: %s", err, summary)}
	}
	return Result{Summary: summary, Err: nil}
}
