package detect

import (
	"fmt"
	"os/exec"
	"sort"
	"strings"
)

// ProviderSpec 内置运行时协议定义。
type ProviderSpec struct {
	ID   string
	Bins []string // 本机可执行名（任一命中即视为已安装）；空表示无需探测
}

// Catalog 主流 + 国产 coding agent 协议目录（自动探测 / 自定义基础协议共用）。
var Catalog = []ProviderSpec{
	{ID: "cursor", Bins: []string{"cursor", "cursor-agent", "agent"}},
	{ID: "claude_code", Bins: []string{"claude", "claude-code"}},
	{ID: "codex", Bins: []string{"codex"}},
	{ID: "opencode", Bins: []string{"opencode"}},
	{ID: "gemini", Bins: []string{"gemini"}},
	{ID: "copilot", Bins: []string{"copilot"}},
	{ID: "aider", Bins: []string{"aider"}},
	{ID: "goose", Bins: []string{"goose"}},
	{ID: "codebuddy", Bins: []string{"codebuddy"}},
	{ID: "qwen", Bins: []string{"qwen", "qwen-code"}},
	{ID: "kimi", Bins: []string{"kimi", "kimi-code"}},
	{ID: "qoder", Bins: []string{"qoder", "qodercli"}},
	{ID: "traecli", Bins: []string{"traecli", "trae", "trae-cli"}},
	{ID: "kiro", Bins: []string{"kiro"}},
	{ID: "grok", Bins: []string{"grok"}},
	{ID: "hermes", Bins: []string{"hermes"}},
	{ID: "pi", Bins: []string{"pi"}},
	{ID: "openclaw", Bins: []string{"openclaw"}},
	{ID: "antigravity", Bins: []string{"antigravity"}},
	{ID: "deveco", Bins: []string{"deveco", "hvigorw"}},
	{ID: "stub", Bins: []string{}},
}

// ProviderBins 各 Provider 对应本机可执行名（任一命中即视为已安装）。
var ProviderBins = func() map[string][]string {
	m := make(map[string][]string, len(Catalog))
	for _, p := range Catalog {
		m[p.ID] = p.Bins
	}
	return m
}()

// BaseProviderIDs 基础协议 id 列表（按长度降序，便于 custom_<base>_<hash> 解析）。
func BaseProviderIDs() []string {
	ids := make([]string, 0, len(Catalog))
	for _, p := range Catalog {
		ids = append(ids, p.ID)
	}
	sort.Slice(ids, func(i, j int) bool {
		if len(ids[i]) != len(ids[j]) {
			return len(ids[i]) > len(ids[j])
		}
		return ids[i] < ids[j]
	})
	return ids
}

// BuiltinProviders 内置 Provider：Daemon 轮询时自动探测，本机已安装则会注册/恢复。
func BuiltinProviders() []string {
	out := make([]string, 0, len(Catalog))
	for _, p := range Catalog {
		if p.ID == "stub" {
			continue
		}
		out = append(out, p.ID)
	}
	return out
}

// AllowedProviders 可注册的 Provider 列表（含 stub）。
func AllowedProviders() []string {
	out := make([]string, 0, len(Catalog))
	for _, p := range Catalog {
		out = append(out, p.ID)
	}
	return out
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

// IsAllowed 是否为已知 Provider（含 custom_<base>_<hash>）。
func IsAllowed(provider string) bool {
	if _, ok := ProviderBins[provider]; ok {
		return true
	}
	return IsCustomProviderKey(provider)
}

// IsCustomProviderKey 自定义运行时 key：custom_<base>_<hash8>
func IsCustomProviderKey(provider string) bool {
	return strings.HasPrefix(provider, "custom_")
}

// BaseProvider 解析执行用的基础协议（自定义项回到对应 base）。
func BaseProvider(provider string) string {
	if !IsCustomProviderKey(provider) {
		return provider
	}
	rest := strings.TrimPrefix(provider, "custom_")
	for _, base := range BaseProviderIDs() {
		if strings.HasPrefix(rest, base+"_") {
			return base
		}
	}
	return provider
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
	if IsCustomProviderKey(provider) {
		base := BaseProvider(provider)
		if !IsBuiltin(base) && base != "stub" {
			return fmt.Errorf("不支持的自定义基础协议: %s", base)
		}
		return nil
	}
	if !IsAllowed(provider) {
		return fmt.Errorf("不支持的 Provider: %s（可选: %s）", provider, strings.Join(AllowedProviders(), ", "))
	}
	if IsInstalled(provider) {
		return nil
	}
	bins := ProviderBins[provider]
	hint := fmt.Sprintf("本机未安装 %s，无法注册。请先安装并将其加入 PATH（查找命令: %s）", provider, strings.Join(bins, " / "))
	switch provider {
	case "claude_code":
		hint += "。若已 npm i -g @anthropic-ai/claude-code，请确认 which claude"
	case "opencode":
		hint += "。可试：npm i -g opencode-ai 或查看 https://opencode.ai"
	case "gemini":
		hint += "。可试：npm i -g @google/gemini-cli"
	case "qwen":
		hint += "。可试：npm i -g @qwen-code/qwen-code"
	case "codebuddy":
		hint += "。可试：npm i -g @tencent-ai/codebuddy-code"
	case "kimi":
		hint += "。可试：npm i -g @moonshot-ai/kimi-code"
	}
	return fmt.Errorf("%s", hint)
}

// DetectProviders 扫描本机已安装的 Provider（含 stub）；诊断与 Daemon 自动同步共用探测逻辑。
func DetectProviders() []string {
	return append([]string{"stub"}, InstalledBuiltins()...)
}
