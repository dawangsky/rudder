package execenv

import (
	"fmt"
	"os"
	"path/filepath"
	"sync"
)

var pathLocks sync.Map

// Prepare 创建沙箱目录并注入 Skill；若 localPathMode 则 cwd 使用服务端给出的 workDir。
func Prepare(envRoot, workDir string, localPathMode bool, skills []map[string]any, instructions string) error {
	if err := os.MkdirAll(filepath.Join(envRoot, "output"), 0o755); err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Join(envRoot, "logs"), 0o755); err != nil {
		return err
	}
	if !localPathMode {
		if err := os.MkdirAll(workDir, 0o755); err != nil {
			return err
		}
	}
	// 注入 instructions
	if instructions != "" {
		_ = os.WriteFile(filepath.Join(workDir, "RUDDER_INSTRUCTIONS.md"), []byte(instructions), 0o644)
	}
	skillsDir := filepath.Join(workDir, ".rudder", "skills")
	_ = os.MkdirAll(skillsDir, 0o755)
	for _, s := range skills {
		name, _ := s["name"].(string)
		content, _ := s["content"].(string)
		if name == "" {
			continue
		}
		_ = os.WriteFile(filepath.Join(skillsDir, name+".md"), []byte(content), 0o644)
	}
	return nil
}

// LockPath 对同一真实路径串行。
func LockPath(path string) func() {
	key := path
	if abs, err := filepath.Abs(path); err == nil {
		key = abs
	}
	muIface, _ := pathLocks.LoadOrStore(key, &sync.Mutex{})
	mu := muIface.(*sync.Mutex)
	mu.Lock()
	return mu.Unlock
}

func AppendLog(envRoot, line string) {
	f := filepath.Join(envRoot, "logs", "daemon.log")
	_ = os.MkdirAll(filepath.Dir(f), 0o755)
	fp, err := os.OpenFile(f, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o644)
	if err != nil {
		return
	}
	defer fp.Close()
	_, _ = fmt.Fprintln(fp, line)
}
