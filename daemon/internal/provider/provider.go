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

// Run 按 provider 执行；找不到真实 CLI 时回退 stub，保证端到端可冒烟。
func Run(provider, workDir, prompt, instructions string) Result {
	switch provider {
	case "claude_code":
		if bin, err := exec.LookPath("claude"); err == nil {
			return runCmd(bin, workDir, []string{"-p", prompt}, map[string]string{})
		}
	case "codex":
		if bin, err := exec.LookPath("codex"); err == nil {
			return runCmd(bin, workDir, []string{"exec", prompt}, map[string]string{})
		}
	case "cursor":
		for _, b := range []string{"cursor-agent", "agent", "cursor"} {
			if bin, err := exec.LookPath(b); err == nil {
				return runCmd(bin, workDir, []string{prompt}, map[string]string{})
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
