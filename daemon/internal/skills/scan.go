package skills

import (
	"crypto/sha256"
	"encoding/hex"
	"os"
	"path/filepath"
	"regexp"
	"strings"
)

const maxDepth = 6
const maxFileBytes = 512 * 1024

var frontmatterRe = regexp.MustCompile(`(?s)^---\s*\r?\n(.*?)\r?\n---\s*\r?\n?`)

// Skill 本机发现的一条 skill。
type Skill struct {
	Name        string `json:"name"`
	Description string `json:"description"`
	Content     string `json:"content"`
	SourcePath  string `json:"sourcePath"`
	ContentHash string `json:"contentHash"`
}

// ScanLocal 扫描常见 skill 根目录下的 SKILL.md。
func ScanLocal() []Skill {
	home, err := os.UserHomeDir()
	if err != nil || home == "" {
		return nil
	}
	roots := []string{
		filepath.Join(home, ".agents", "skills"),
		filepath.Join(home, ".openclaw", "skills"),
		filepath.Join(home, ".claude", "skills"),
		filepath.Join(home, ".cursor", "skills"),
		filepath.Join(home, ".cursor", "skills-cursor"),
	}
	if codex := os.Getenv("CODEX_HOME"); codex != "" {
		roots = append(roots, filepath.Join(codex, "skills"))
	} else {
		roots = append(roots, filepath.Join(home, ".codex", "skills"))
	}

	seen := map[string]struct{}{}
	var out []Skill
	for _, root := range roots {
		info, err := os.Stat(root)
		if err != nil || !info.IsDir() {
			continue
		}
		_ = filepath.WalkDir(root, func(path string, d os.DirEntry, err error) error {
			if err != nil {
				return nil
			}
			rel, relErr := filepath.Rel(root, path)
			if relErr != nil {
				return nil
			}
			if d.IsDir() {
				depth := 0
				if rel != "." {
					depth = strings.Count(rel, string(os.PathSeparator)) + 1
				}
				if depth > maxDepth {
					return filepath.SkipDir
				}
				return nil
			}
			if !strings.EqualFold(d.Name(), "SKILL.md") {
				return nil
			}
			abs, _ := filepath.Abs(path)
			if _, ok := seen[abs]; ok {
				return nil
			}
			seen[abs] = struct{}{}
			if sk, ok := readSkill(path); ok {
				out = append(out, sk)
			}
			return nil
		})
	}
	return out
}

func readSkill(path string) (Skill, bool) {
	st, err := os.Stat(path)
	if err != nil || st.Size() <= 0 || st.Size() > maxFileBytes {
		return Skill{}, false
	}
	b, err := os.ReadFile(path)
	if err != nil || len(b) == 0 {
		return Skill{}, false
	}
	content := string(b)
	name, desc := parseFrontmatter(content)
	if name == "" {
		name = filepath.Base(filepath.Dir(path))
	}
	sum := sha256.Sum256(b)
	return Skill{
		Name:        name,
		Description: desc,
		Content:     content,
		SourcePath:  path,
		ContentHash: hex.EncodeToString(sum[:8]),
	}, true
}

func parseFrontmatter(content string) (name, description string) {
	m := frontmatterRe.FindStringSubmatch(content)
	if len(m) < 2 {
		return "", ""
	}
	for _, line := range strings.Split(m[1], "\n") {
		line = strings.TrimSpace(line)
		if strings.HasPrefix(line, "name:") {
			name = unquote(strings.TrimSpace(strings.TrimPrefix(line, "name:")))
		}
		if strings.HasPrefix(line, "description:") {
			description = unquote(strings.TrimSpace(strings.TrimPrefix(line, "description:")))
		}
	}
	return name, description
}

func unquote(s string) string {
	if len(s) >= 2 {
		if (s[0] == '"' && s[len(s)-1] == '"') || (s[0] == '\'' && s[len(s)-1] == '\'') {
			return s[1 : len(s)-1]
		}
	}
	return s
}
