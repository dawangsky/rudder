package config

import (
	"encoding/json"
	"os"
	"path/filepath"
)

type Credentials struct {
	Server      string `json:"server"`
	Email       string `json:"email"`
	DaemonToken string `json:"daemonToken"`
}

func LoadCredentials() (*Credentials, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return nil, err
	}
	path := filepath.Join(home, ".rudder", "credentials.json")
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

func PidPath() (string, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}
	dir := filepath.Join(home, ".rudder")
	_ = os.MkdirAll(dir, 0o700)
	return filepath.Join(dir, "daemon.pid"), nil
}
