package detect

import (
	"fmt"
	"os/exec"
	"strings"
)

// ProviderBins 各 Provider 对应本机可执行名（任一命中即视为已安装）。
var ProviderBins = map[string][]string{
	"stub":        {}, // stub 无需真实 CLI
	"cursor":      {"cursor", "cursor-agent", "agent"},
	"claude_code": {"claude", "claude-code"},
	"codex":       {"codex"},
}

// AllowedProviders 可手动添加的 Provider 列表。
func AllowedProviders() []string {
	return []string{"stub", "cursor", "claude_code", "codex"}
}

// IsAllowed 是否为已知 Provider。
func IsAllowed(provider string) bool {
	_, ok := ProviderBins[provider]
	return ok
}

// IsInstalled 探测本机是否已安装该 Provider（stub 恒为 true）。
func IsInstalled(provider string) bool {
	if provider == "stub" {
		return true
	}
	bins, ok := ProviderBins[provider]
	if !ok {
		return false
	}
	for _, b := range bins {
		if _, err := exec.LookPath(b); err == nil {
			return true
		}
	}
	return false
}

// RequireInstalled 未安装则返回可读错误（用于「添加运行时」注册失败提示）。
func RequireInstalled(provider string) error {
	if !IsAllowed(provider) {
		return fmt.Errorf("不支持的 Provider: %s（可选: %s）", provider, strings.Join(AllowedProviders(), ", "))
	}
	if IsInstalled(provider) {
		return nil
	}
	bins := ProviderBins[provider]
	return fmt.Errorf("本机未安装 %s，无法注册。请先安装并将其加入 PATH（查找命令: %s）", provider, strings.Join(bins, " / "))
}

// DetectProviders 扫描本机已安装的 Provider（含 stub）；仅用于诊断，不再在 Daemon 启动时全量注册。
func DetectProviders() []string {
	found := []string{"stub"}
	for _, p := range []string{"cursor", "claude_code", "codex"} {
		if IsInstalled(p) {
			found = append(found, p)
		}
	}
	return found
}
