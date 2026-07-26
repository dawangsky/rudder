package detect

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

// SplitCommand 简单按空白拆分命令（支持双引号片段）。
func SplitCommand(cmdline string) ([]string, error) {
	cmdline = strings.TrimSpace(cmdline)
	if cmdline == "" {
		return nil, fmt.Errorf("命令不能为空")
	}
	var parts []string
	var cur strings.Builder
	inQuote := false
	escape := false
	for _, r := range cmdline {
		switch {
		case escape:
			cur.WriteRune(r)
			escape = false
		case r == '\\':
			escape = true
		case r == '"':
			inQuote = !inQuote
		case (r == ' ' || r == '\t') && !inQuote:
			if cur.Len() > 0 {
				parts = append(parts, cur.String())
				cur.Reset()
			}
		default:
			cur.WriteRune(r)
		}
	}
	if inQuote {
		return nil, fmt.Errorf("命令引号未闭合")
	}
	if cur.Len() > 0 {
		parts = append(parts, cur.String())
	}
	if len(parts) == 0 {
		return nil, fmt.Errorf("命令不能为空")
	}
	return parts, nil
}

// ValidateCommand 校验命令可执行：首段为绝对路径则检查存在，否则 LookPath。
func ValidateCommand(cmdline string) error {
	parts, err := SplitCommand(cmdline)
	if err != nil {
		return err
	}
	bin := parts[0]
	if filepath.IsAbs(bin) || strings.Contains(bin, string(filepath.Separator)) {
		st, err := os.Stat(bin)
		if err != nil {
			return fmt.Errorf("找不到命令文件: %s", bin)
		}
		if st.IsDir() {
			return fmt.Errorf("命令路径是目录: %s", bin)
		}
		return nil
	}
	if _, err := exec.LookPath(bin); err != nil {
		return fmt.Errorf("本机 PATH 中找不到命令「%s」，请确认已安装或使用绝对路径", bin)
	}
	return nil
}
