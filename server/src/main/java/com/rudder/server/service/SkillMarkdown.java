package com.rudder.server.service;

import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** SKILL.md frontmatter 轻量解析（name / description）。 */
public final class SkillMarkdown {

    private static final Pattern FRONTMATTER = Pattern.compile(
            "^---\\s*\\r?\\n([\\s\\S]*?)\\r?\\n---\\s*\\r?\\n?",
            Pattern.MULTILINE);

    private SkillMarkdown() {}

    public record Parsed(String name, String description, String content) {}

    public static Parsed parse(String content) {
        String raw = content == null ? "" : content.trim();
        if (raw.isEmpty()) {
            return new Parsed("", "", "");
        }
        Matcher m = FRONTMATTER.matcher(raw);
        if (!m.find()) {
            return new Parsed("", "", raw);
        }
        Map<String, String> meta = parseYamlSimple(m.group(1));
        String name = meta.getOrDefault("name", "").trim();
        String desc = meta.getOrDefault("description", "").trim();
        return new Parsed(name, desc, raw);
    }

    /**
     * 同步 frontmatter 中的 name / description；无 frontmatter 时在文首补一段。
     */
    public static String syncFrontmatter(String content, String name, String description) {
        String raw = content == null ? "" : content;
        String safeName = name == null ? "" : name.trim();
        String safeDesc = description == null ? "" : description.trim();
        Matcher m = FRONTMATTER.matcher(raw.stripLeading());
        if (!m.find()) {
            StringBuilder sb = new StringBuilder();
            sb.append("---\n");
            sb.append("name: ").append(yamlScalar(safeName)).append('\n');
            if (StringUtils.hasText(safeDesc)) {
                sb.append("description: ").append(yamlBlockOrScalar(safeDesc)).append('\n');
            }
            sb.append("---\n\n");
            sb.append(raw.stripLeading());
            return sb.toString();
        }
        String yaml = m.group(1);
        String body = raw.stripLeading().substring(m.end());
        Map<String, String> meta = parseYamlSimple(yaml);
        meta.put("name", safeName);
        if (StringUtils.hasText(safeDesc)) {
            meta.put("description", safeDesc);
        } else {
            meta.remove("description");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        // 保持常见顺序：name → description → 其他
        if (meta.containsKey("name")) {
            sb.append("name: ").append(yamlScalar(meta.get("name"))).append('\n');
        }
        if (meta.containsKey("description")) {
            sb.append("description: ").append(yamlBlockOrScalar(meta.get("description"))).append('\n');
        }
        for (Map.Entry<String, String> e : meta.entrySet()) {
            if ("name".equals(e.getKey()) || "description".equals(e.getKey())) continue;
            sb.append(e.getKey()).append(": ").append(yamlScalar(e.getValue())).append('\n');
        }
        sb.append("---\n");
        if (!body.isEmpty() && !body.startsWith("\n")) sb.append('\n');
        sb.append(body);
        return sb.toString();
    }

    private static String yamlScalar(String v) {
        if (v == null) return "\"\"";
        if (v.isEmpty()) return "\"\"";
        if (v.indexOf(':') >= 0 || v.indexOf('#') >= 0 || v.indexOf('\n') >= 0
                || v.startsWith(" ") || v.endsWith(" ")) {
            return "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return v;
    }

    private static String yamlBlockOrScalar(String v) {
        if (v != null && v.contains("\n")) {
            StringBuilder sb = new StringBuilder("|-\n");
            for (String line : v.split("\\r?\\n", -1)) {
                sb.append("  ").append(line).append('\n');
            }
            return sb.toString().stripTrailing();
        }
        return yamlScalar(v);
    }

    /** 极简 YAML：只取顶层 key: value / key: | 多行首行。 */
    static Map<String, String> parseYamlSimple(String yaml) {
        Map<String, String> out = new HashMap<>();
        if (!StringUtils.hasText(yaml)) return out;
        String[] lines = yaml.split("\\r?\\n");
        String currentKey = null;
        StringBuilder block = null;
        for (String line : lines) {
            if (block != null) {
                if (line.startsWith("  ") || line.startsWith("\t") || line.isBlank()) {
                    if (!block.isEmpty()) block.append('\n');
                    block.append(line.replaceFirst("^[ \\t]", "").replaceFirst("^ ", ""));
                    continue;
                }
                out.put(currentKey, block.toString().trim());
                block = null;
                currentKey = null;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String key = line.substring(0, colon).trim();
            String val = line.substring(colon + 1).trim();
            if (val.equals("|") || val.equals(">") || val.equals("|-") || val.equals(">-")) {
                currentKey = key;
                block = new StringBuilder();
                continue;
            }
            if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                val = val.substring(1, val.length() - 1);
            }
            out.put(key, val);
        }
        if (block != null && currentKey != null) {
            out.put(currentKey, block.toString().trim());
        }
        return out;
    }
}
