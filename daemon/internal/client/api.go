package client

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

type API struct {
	Base  string
	Token string
	HTTP  *http.Client
}

func New(base, token string) *API {
	return &API{Base: base, Token: token, HTTP: &http.Client{Timeout: 30 * time.Second}}
}

func (a *API) do(method, path string, body any, out any) error {
	var reader io.Reader
	if body != nil {
		b, _ := json.Marshal(body)
		reader = bytes.NewReader(b)
	}
	req, err := http.NewRequest(method, a.Base+path, reader)
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+a.Token)
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	resp, err := a.HTTP.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	data, _ := io.ReadAll(resp.Body)
	if resp.StatusCode >= 300 {
		return fmt.Errorf("%s %s -> %s: %s", method, path, resp.Status, string(data))
	}
	if out == nil || len(data) == 0 {
		return nil
	}
	return json.Unmarshal(data, out)
}

func (a *API) RegisterRuntime(daemonID, provider, host, metaJSON string) (map[string]any, error) {
	if metaJSON == "" {
		metaJSON = "{}"
	}
	var out map[string]any
	err := a.do(http.MethodPost, "/api/daemon/runtimes", map[string]string{
		"daemonId": daemonID,
		"provider": provider,
		"hostName": host,
		"metaJson": metaJSON,
	}, &out)
	return out, err
}

func (a *API) Heartbeat(runtimeID string) error {
	return a.do(http.MethodPost, "/api/daemon/heartbeat", map[string]string{"runtimeId": runtimeID}, nil)
}

func (a *API) Claim(runtimeID string) (map[string]any, error) {
	var out map[string]any
	err := a.do(http.MethodPost, "/api/daemon/claim", map[string]string{"runtimeId": runtimeID}, &out)
	return out, err
}

func (a *API) Report(taskID string, body map[string]any) error {
	return a.do(http.MethodPost, "/api/daemon/tasks/"+taskID+"/report", body, nil)
}

// DeleteRuntimeByProvider 删除当前 Daemon 实例下该 Provider 的运行时（不误删其它 profile）。
func (a *API) DeleteRuntimeByProvider(daemonID, provider string) error {
	path := "/api/daemon/runtimes/provider/" + provider + "?daemonId=" + daemonID
	return a.do(http.MethodDelete, path, nil, nil)
}

// ReportSkills 上报本机扫描到的 skill 列表（按 runtime 整包替换）。
func (a *API) ReportSkills(daemonID, runtimeID string, skills []map[string]any) error {
	return a.do(http.MethodPost, "/api/daemon/skills/report", map[string]any{
		"daemonId":  daemonID,
		"runtimeId": runtimeID,
		"skills":    skills,
	}, nil)
}

// ProtocolSpec Server 下发的工作区启用协议。
type ProtocolSpec struct {
	Code  string   `json:"code"`
	Bins  []string `json:"bins"`
	Label string   `json:"label"`
}

// ListProtocols 拉取当前工作区已启用的运行时协议目录。
func (a *API) ListProtocols() ([]ProtocolSpec, error) {
	var out []ProtocolSpec
	err := a.do(http.MethodGet, "/api/daemon/protocols", nil, &out)
	return out, err
}
