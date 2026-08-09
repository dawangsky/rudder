package skills

import (
	"crypto/sha256"
	"encoding/hex"
	"io/fs"
	"os"
	"path/filepath"
	"regexp"
	"strings"
)

const maxDepth = 6
const maxFileBytes = 512 * 1024
const maxSkillDirBytes = 8 * 1024 * 1024
const maxSkillDirFiles = 200

var frontmatterRe = regexp.MustCompile(`(?s)^---\s*\r?\n(.*?)\r?\n---\s*\r?\n?`)

// Skill 本机发现的一条 skill。
type Skill struct {
	Name        string `json:"name"`
	Description string `json:"description"`
	Content     string `json:"content"`
	SourcePath  string `json:"sourcePath"`
	ContentHash string `json:"contentHash"`
}

func isSkippedDirName(name string) bool {
	return name == "." || name == ".." || strings.HasPrefix(name, ".")
}

// ScanLocal 扫描常见 skill 根目录下的 SKILL.md。
// 忽略软链、点目录（含 Codex .system）、不可读、超大文件与超大目录。
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
	}
	if codex := os.Getenv("CODEX_HOME"); codex != "" {
		roots = append(roots, filepath.Join(codex, "skills"))
	} else {
		roots = append(roots, filepath.Join(home, ".codex", "skills"))
	}

	seen := map[string]struct{}{}
	var out []Skill
	for _, root := range roots {
		info, err := os.Lstat(root)
		if err != nil || info.Mode()&os.ModeSymlink != 0 || !info.IsDir() {
			continue
		}
		_ = filepath.WalkDir(root, func(path string, d fs.DirEntry, err error) error {
			if err != nil {
				return nil
			}
			// 不跟随软链
			if d.Type()&fs.ModeSymlink != 0 {
				if d.IsDir() {
					return filepath.SkipDir
				}
				return nil
			}
			rel, relErr := filepath.Rel(root, path)
			if relErr != nil {
				return nil
			}
			if d.IsDir() {
				name := d.Name()
				if path != root && isSkippedDirName(name) {
					return filepath.SkipDir
				}
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
			abs, absErr := filepath.Abs(path)
			if absErr != nil {
				abs = path
			}
			if _, ok := seen[abs]; ok {
				return nil
			}
			if sk, ok := readSkill(path); ok {
				seen[abs] = struct{}{}
				out = append(out, sk)
			}
			return nil
		})
	}
	return out
}

func readSkill(path string) (Skill, bool) {
	st, err := os.Lstat(path)
	if err != nil || st.Mode()&os.ModeSymlink != 0 || !st.Mode().IsRegular() {
		return Skill{}, false
	}
	if st.Size() <= 0 || st.Size() > maxFileBytes {
		return Skill{}, false
	}
	if !measureSkillDirOK(filepath.Dir(path)) {
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

func measureSkillDirOK(dir string) bool {
	var files int
	var bytes int64
	err := filepath.WalkDir(dir, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			// 不可读条目跳过，不整包失败
			return nil
		}
		if d.Type()&fs.ModeSymlink != 0 {
			if d.IsDir() {
				return filepath.SkipDir
			}
			return nil
		}
		if d.IsDir() {
			if path != dir && isSkippedDirName(d.Name()) {
				return filepath.SkipDir
			}
			rel, relErr := filepath.Rel(dir, path)
			if relErr == nil && rel != "." {
				depth := strings.Count(rel, string(os.PathSeparator)) + 1
				if depth > maxDepth {
					return filepath.SkipDir
				}
			}
			return nil
		}
		if d.Name() == ".DS_Store" {
			return nil
		}
		info, infoErr := d.Info()
		if infoErr != nil || !info.Mode().IsRegular() {
			return nil
		}
		if info.Size() > maxFileBytes {
			return fs.SkipAll
		}
		files++
		bytes += info.Size()
		if files > maxSkillDirFiles || bytes > maxSkillDirBytes {
			return fs.SkipAll
		}
		return nil
	})
	if err != nil {
		return false
	}
	return true
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
