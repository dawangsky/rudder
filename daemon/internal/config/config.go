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

func RudderHome() (string, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}
	dir := filepath.Join(home, ".rudder")
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

func PidPath() (string, error) {
	dir, err := RudderHome()
	if err != nil {
		return "", err
	}
	return filepath.Join(dir, "daemon.pid"), nil
}
