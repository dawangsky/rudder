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

// BuiltinProviders 内置 Provider：Daemon 轮询时自动探测，本机已安装则会注册/恢复。
func BuiltinProviders() []string {
	return []string{"cursor", "claude_code", "codex"}
}

// AllowedProviders 可注册的 Provider 列表（含 stub / 自定义手动项）。
func AllowedProviders() []string {
	return []string{"stub", "cursor", "claude_code", "codex"}
}

// IsBuiltin 是否为自动探测的内置 Provider。
func IsBuiltin(provider string) bool {
	for _, p := range BuiltinProviders() {
		if p == provider {
			return true
		}
	}
	return false
}

// InstalledBuiltins 返回本机已安装的内置 Provider（不含 stub）。
func InstalledBuiltins() []string {
	var found []string
	for _, p := range BuiltinProviders() {
		if IsInstalled(p) {
			found = append(found, p)
		}
	}
	return found
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
	hint := fmt.Sprintf("本机未安装 %s，无法注册。请先安装并将其加入 PATH（查找命令: %s）", provider, strings.Join(bins, " / "))
	if provider == "claude_code" {
		hint += "。若已 npm i -g @anthropic-ai/claude-code，请确认 ~/.npm-global/bin/claude 可执行（which claude）"
	}
	return fmt.Errorf("%s", hint)
}

// DetectProviders 扫描本机已安装的 Provider（含 stub）；诊断与 Daemon 自动同步共用探测逻辑。
func DetectProviders() []string {
	return append([]string{"stub"}, InstalledBuiltins()...)
}
