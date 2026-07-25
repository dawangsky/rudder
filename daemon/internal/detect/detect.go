package detect

import (
	"os/exec"
)

// DetectProviders 扫描本机 PATH 上常见 Agent CLI；始终包含 stub 以便无 CLI 时仍可冒烟。
func DetectProviders() []string {
	found := []string{"stub"}
	candidates := map[string][]string{
		"cursor":      {"cursor", "cursor-agent", "agent"},
		"claude_code": {"claude", "claude-code"},
		"codex":       {"codex"},
	}
	for provider, bins := range candidates {
		for _, b := range bins {
			if _, err := exec.LookPath(b); err == nil {
				found = append(found, provider)
				break
			}
		}
	}
	return found
}
