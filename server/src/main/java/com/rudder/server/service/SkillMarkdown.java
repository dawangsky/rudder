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
        String body = raw.substring(m.end()).trim();
        String name = meta.getOrDefault("name", "").trim();
        String desc = meta.getOrDefault("description", "").trim();
        return new Parsed(name, desc, raw);
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
