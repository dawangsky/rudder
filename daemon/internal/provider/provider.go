package provider

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"

	"github.com/dawangsky/rudder/daemon/internal/detect"
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
	cmd.Env = append(os.Environ(), rudderEnv(prompt, instructions, model, thinkingMode)...)
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

func rudderEnv(prompt, instructions, model, thinkingMode string) []string {
	return []string{
		"RUDDER_PROMPT=" + prompt,
		"RUDDER_INSTRUCTIONS=" + instructions,
		"RUDDER_MODEL=" + model,
		"RUDDER_THINKING_MODE=" + thinkingMode,
	}
}

func envMap(prompt, instructions, model, thinkingMode string) map[string]string {
	return map[string]string{
		"RUDDER_PROMPT":        prompt,
		"RUDDER_INSTRUCTIONS":  instructions,
		"RUDDER_MODEL":         model,
		"RUDDER_THINKING_MODE": thinkingMode,
	}
}

// runSpec 各协议默认无头调用方式；找不到 CLI 时回退 stub。
type runSpec struct {
	bins []string
	args func(prompt string) []string
}

var runSpecs = map[string]runSpec{
	"claude_code": {bins: []string{"claude", "claude-code"}, args: func(p string) []string { return []string{"-p", p} }},
	"codex":       {bins: []string{"codex"}, args: func(p string) []string { return []string{"exec", p} }},
	"cursor":      {bins: []string{"cursor-agent", "agent", "cursor"}, args: func(p string) []string { return []string{p} }},
	"opencode":    {bins: []string{"opencode"}, args: func(p string) []string { return []string{"run", p} }},
	"gemini":      {bins: []string{"gemini"}, args: func(p string) []string { return []string{"-p", p} }},
	"copilot":     {bins: []string{"copilot"}, args: func(p string) []string { return []string{"-p", p} }},
	"aider":       {bins: []string{"aider"}, args: func(p string) []string { return []string{"--message", p, "--yes-always"} }},
	"goose":       {bins: []string{"goose"}, args: func(p string) []string { return []string{"run", p} }},
	"codebuddy":   {bins: []string{"codebuddy"}, args: func(p string) []string { return []string{"-p", p} }},
	"qwen":        {bins: []string{"qwen", "qwen-code"}, args: func(p string) []string { return []string{"-p", p} }},
	"kimi":        {bins: []string{"kimi", "kimi-code"}, args: func(p string) []string { return []string{"-p", p} }},
	"qoder":       {bins: []string{"qoder", "qodercli"}, args: func(p string) []string { return []string{"-p", p} }},
	"traecli":     {bins: []string{"traecli", "trae", "trae-cli"}, args: func(p string) []string { return []string{"-p", p} }},
	"kiro":        {bins: []string{"kiro"}, args: func(p string) []string { return []string{"-p", p} }},
	"grok":        {bins: []string{"grok"}, args: func(p string) []string { return []string{"-p", p} }},
	"hermes":      {bins: []string{"hermes"}, args: func(p string) []string { return []string{"-p", p} }},
	"pi":          {bins: []string{"pi"}, args: func(p string) []string { return []string{"-p", p} }},
	"openclaw":    {bins: []string{"openclaw"}, args: func(p string) []string { return []string{"-p", p} }},
	"antigravity": {bins: []string{"antigravity"}, args: func(p string) []string { return []string{"-p", p} }},
	"deveco":      {bins: []string{"deveco"}, args: func(p string) []string { return []string{"-p", p} }},
}

// Run 按 provider 执行；找不到真实 CLI 时回退 stub，保证端到端可冒烟。
func Run(provider, workDir, prompt, instructions, model, thinkingMode string) Result {
	env := envMap(prompt, instructions, model, thinkingMode)
	base := detect.BaseProvider(provider)
	if base == "stub" {
		return runStub(workDir, provider, prompt, instructions)
	}
	if spec, ok := runSpecs[base]; ok {
		for _, b := range spec.bins {
			if bin, err := detect.LookPath(b); err == nil {
				return runCmd(bin, workDir, spec.args(prompt), env)
			}
		}
	}
	// 未知协议：尝试用 Catalog 中的 bin + 直接传 prompt
	if bins, ok := detect.ProviderBins[base]; ok {
		for _, b := range bins {
			if bin, err := detect.LookPath(b); err == nil {
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
