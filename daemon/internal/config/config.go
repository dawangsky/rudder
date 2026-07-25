package config

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"

	"github.com/google/uuid"
)

// Credentials 本机 Daemon 凭证（按 profile 分目录存放）。
type Credentials struct {
	Server      string `json:"server"`
	Email       string `json:"email"`
	DaemonToken string `json:"daemonToken"`
}

// activeProfile 空/"default"/"cli" → ~/.rudder；其它 → ~/.rudder/profiles/{name}
var activeProfile string

// SetProfile 设置当前 profile（CLI --profile 或环境变量 RUDDER_PROFILE）。
func SetProfile(name string) {
	name = strings.TrimSpace(name)
	if name == "" {
		name = strings.TrimSpace(os.Getenv("RUDDER_PROFILE"))
	}
	switch strings.ToLower(name) {
	case "", "default", "cli":
		activeProfile = ""
	default:
		activeProfile = name
	}
}

// ProfileName 返回当前 profile 名；默认 CLI 返回 "cli"。
func ProfileName() string {
	if activeProfile == "" {
		return "cli"
	}
	return activeProfile
}

// RudderHome 当前 profile 的数据根目录。
func RudderHome() (string, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}
	base := filepath.Join(home, ".rudder")
	var dir string
	if activeProfile == "" {
		dir = base
	} else {
		dir = filepath.Join(base, "profiles", activeProfile)
	}
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return "", err
	}
	return dir, nil
}

func LoadCredentials() (*Credentials, error) {
	dir, err := RudderHome()
	if err != nil {
		return nil, err
	}
	path := filepath.Join(dir, "credentials.json")
	raw, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var c Credentials
	if err := json.Unmarshal(raw, &c); err != nil {
		return nil, err
	}
	return &c, nil
}

// SaveCredentials 写入当前 profile 凭证。
func SaveCredentials(c *Credentials) (string, error) {
	dir, err := RudderHome()
	if err != nil {
		return "", err
	}
	path := filepath.Join(dir, "credentials.json")
	raw, _ := json.MarshalIndent(c, "", "  ")
	if err := os.WriteFile(path, raw, 0o600); err != nil {
		return "", err
	}
	return path, nil
}

func PidPath() (string, error) {
	dir, err := RudderHome()
	if err != nil {
		return "", err
	}
	return filepath.Join(dir, "daemon.pid"), nil
}

type instanceFile struct {
	ID      string `json:"id"`
	Profile string `json:"profile"`
}

// LoadOrCreateInstanceID 返回本 profile 稳定的 daemon 实例 ID（持久化到 instance.json）。
func LoadOrCreateInstanceID() (string, error) {
	dir, err := RudderHome()
	if err != nil {
		return "", err
	}
	path := filepath.Join(dir, "instance.json")
	if raw, err := os.ReadFile(path); err == nil {
		var f instanceFile
		if json.Unmarshal(raw, &f) == nil && strings.TrimSpace(f.ID) != "" {
			return f.ID, nil
		}
	}
	id := uuid.NewString()
	f := instanceFile{ID: id, Profile: ProfileName()}
	raw, _ := json.MarshalIndent(f, "", "  ")
	if err := os.WriteFile(path, raw, 0o600); err != nil {
		return "", err
	}
	return id, nil
}
