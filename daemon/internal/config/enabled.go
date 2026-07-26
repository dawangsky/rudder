package config

import (
	"encoding/json"
	"os"
	"path/filepath"
	"sort"
)

// EnabledProvidersPath 本机启用列表：内置项可由 Daemon 探测自动写入；stub 等须手动 add。
func EnabledProvidersPath() (string, error) {
	dir, err := RudderHome()
	if err != nil {
		return "", err
	}
	return filepath.Join(dir, "enabled_providers.json"), nil
}

// LoadEnabledProviders 读取已启用 Provider。
func LoadEnabledProviders() ([]string, error) {
	path, err := EnabledProvidersPath()
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
	var list []string
	if err := json.Unmarshal(raw, &list); err != nil {
		return nil, err
	}
	return list, nil
}

// SaveEnabledProviders 覆盖写入已启用列表。
func SaveEnabledProviders(providers []string) error {
	path, err := EnabledProvidersPath()
	if err != nil {
		return err
	}
	sort.Strings(providers)
	b, _ := json.MarshalIndent(providers, "", "  ")
	return os.WriteFile(path, b, 0o600)
}

// AddEnabledProvider 追加（去重）。
func AddEnabledProvider(provider string) error {
	list, err := LoadEnabledProviders()
	if err != nil {
		return err
	}
	for _, p := range list {
		if p == provider {
			return SaveEnabledProviders(list)
		}
	}
	list = append(list, provider)
	return SaveEnabledProviders(list)
}

// RemoveEnabledProvider 从本地启用列表移除。
func RemoveEnabledProvider(provider string) error {
	list, err := LoadEnabledProviders()
	if err != nil {
		return err
	}
	out := make([]string, 0, len(list))
	for _, p := range list {
		if p != provider {
			out = append(out, p)
		}
	}
	return SaveEnabledProviders(out)
}
