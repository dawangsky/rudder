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

func (a *API) RegisterRuntime(daemonID, provider, host string) (map[string]any, error) {
	var out map[string]any
	err := a.do(http.MethodPost, "/api/daemon/runtimes", map[string]string{
		"daemonId": daemonID,
		"provider": provider,
		"hostName": host,
		"metaJson": "{}",
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
