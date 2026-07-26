package config

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

// CustomRuntime 本机自定义运行时（覆盖启动命令；基础协议仍为 cursor/claude_code/codex）。
type CustomRuntime struct {
	ProviderKey  string `json:"providerKey"`
	BaseProvider string `json:"baseProvider"`
	Name         string `json:"name"`
	Command      string `json:"command"`
	Description  string `json:"description,omitempty"`
}

func customRuntimesPath() (string, error) {
	dir, err := RudderHome()
	if err != nil {
		return "", err
	}
	return filepath.Join(dir, "custom_runtimes.json"), nil
}

// LoadCustomRuntimes 读取本机自定义运行时列表。
func LoadCustomRuntimes() ([]CustomRuntime, error) {
	path, err := customRuntimesPath()
	if err != nil {
		return nil, err
	}
	raw, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, nil
		}
		return nil, err
	}
	var list []CustomRuntime
	if err := json.Unmarshal(raw, &list); err != nil {
		return nil, err
	}
	return list, nil
}

func saveCustomRuntimes(list []CustomRuntime) error {
	path, err := customRuntimesPath()
	if err != nil {
		return err
	}
	b, _ := json.MarshalIndent(list, "", "  ")
	return os.WriteFile(path, b, 0o600)
}

// MakeCustomProviderKey 生成唯一 provider：custom_<base>_<hash8>
func MakeCustomProviderKey(base, name, command string) string {
	sum := sha256.Sum256([]byte(strings.TrimSpace(name) + "\x00" + strings.TrimSpace(command)))
	h := hex.EncodeToString(sum[:])[:8]
	base = strings.ToLower(strings.TrimSpace(base))
	return fmt.Sprintf("custom_%s_%s", base, h)
}

// AddCustomRuntime 追加自定义运行时（同 providerKey 则覆盖）。
func AddCustomRuntime(item CustomRuntime) error {
	list, err := LoadCustomRuntimes()
	if err != nil {
		return err
	}
	out := make([]CustomRuntime, 0, len(list)+1)
	replaced := false
	for _, c := range list {
		if c.ProviderKey == item.ProviderKey {
			out = append(out, item)
			replaced = true
		} else {
			out = append(out, c)
		}
	}
	if !replaced {
		out = append(out, item)
	}
	return saveCustomRuntimes(out)
}

// RemoveCustomRuntime 按 providerKey 删除。
func RemoveCustomRuntime(providerKey string) error {
	list, err := LoadCustomRuntimes()
	if err != nil {
		return err
	}
	out := make([]CustomRuntime, 0, len(list))
	for _, c := range list {
		if c.ProviderKey != providerKey {
			out = append(out, c)
		}
	}
	return saveCustomRuntimes(out)
}

// IsCustomProvider 是否为自定义运行时 provider key。
func IsCustomProvider(provider string) bool {
	return strings.HasPrefix(provider, "custom_")
}

// BaseFromCustomProvider 从 custom_<base>_<hash> 解析基础协议。
func BaseFromCustomProvider(provider string) string {
	if !IsCustomProvider(provider) {
		return provider
	}
	rest := strings.TrimPrefix(provider, "custom_")
	for _, base := range []string{"claude_code", "cursor", "codex", "stub"} {
		if strings.HasPrefix(rest, base+"_") {
			return base
		}
	}
	parts := strings.Split(rest, "_")
	if len(parts) >= 2 {
		return parts[0]
	}
	return rest
}
