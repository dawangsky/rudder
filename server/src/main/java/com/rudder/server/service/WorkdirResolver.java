package com.rudder.server.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工作目录解析：项目本地路径优先，否则沙箱路径。
 * 根目录可通过环境变量 RUDDER_WORKSPACES_ROOT 覆盖。
 */
public final class WorkdirResolver {

    private static final Set<String> DENY_PREFIXES = Set.of(
            "/", "/etc", "/usr", "/bin", "/sbin", "/System", "/Library", "/private/etc"
    );

    /**
     * 与 Daemon detect.Catalog 保持一致的基础协议白名单。
     * 含主流（OpenCode / Gemini / Copilot 等）与国产（CodeBuddy / Qwen / Kimi / Qoder / Trae 等）。
     */
    private static final List<String> BASE_PROVIDERS = List.of(
            "cursor", "claude_code", "codex", "opencode", "gemini", "copilot", "aider", "goose",
            "codebuddy", "qwen", "kimi", "qoder", "traecli", "kiro", "grok", "hermes", "pi",
            "openclaw", "antigravity", "deveco", "stub"
    );

    /** 按长度降序，保证 custom_claude_code_xxx 先匹配 claude_code */
    private static final List<String> BASE_PROVIDERS_BY_LEN = BASE_PROVIDERS.stream()
            .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(s -> s))
            .collect(Collectors.toList());

    private static final Set<String> BASE_PROVIDER_SET = Set.copyOf(BASE_PROVIDERS);

    private WorkdirResolver() {
    }

    public static String defaultRoot() {
        String env = System.getenv("RUDDER_WORKSPACES_ROOT");
        if (env != null && !env.isBlank()) {
            return env;
        }
        return Path.of(System.getProperty("user.home"), "rudder_workspaces").toString();
    }

    public static String sandboxWorkdir(long workspaceId, long taskId) {
        return Path.of(defaultRoot(), String.valueOf(workspaceId), String.valueOf(taskId), "workdir").toString();
    }

    public static String sandboxEnvRoot(long workspaceId, long taskId) {
        return Path.of(defaultRoot(), String.valueOf(workspaceId), String.valueOf(taskId)).toString();
    }

    /**
     * @param projectLocalPath 可为 null
     * @return 最终 Agent cwd
     */
    public static String resolve(Long workspaceId, Long taskId, String projectLocalPath) {
        if (projectLocalPath != null && !projectLocalPath.isBlank()) {
            validateLocalPath(projectLocalPath);
            return Path.of(projectLocalPath).toAbsolutePath().normalize().toString();
        }
        return sandboxWorkdir(workspaceId, taskId);
    }

    public static void validateLocalPath(String path) {
        Path p = Path.of(path).toAbsolutePath().normalize();
        String s = p.toString();
        String home = System.getProperty("user.home");
        if (s.equals("/") || s.equals(home) || DENY_PREFIXES.contains(s)) {
            throw new IllegalArgumentException("危险或过宽的本地路径被拒绝: " + s);
        }
        // 禁止系统关键前缀（除用户 home 下目录）
        for (String deny : DENY_PREFIXES) {
            if (!"/".equals(deny) && s.startsWith(deny + "/") && (home == null || !s.startsWith(home))) {
                throw new IllegalArgumentException("路径不在允许范围: " + s);
            }
        }
        if (!Files.exists(p)) {
            throw new IllegalArgumentException("本地路径不存在: " + s);
        }
        if (!Files.isDirectory(p)) {
            throw new IllegalArgumentException("本地路径必须是目录: " + s);
        }
        if (!Files.isWritable(p)) {
            throw new IllegalArgumentException("本地路径不可写: " + s);
        }
    }

    public static boolean isAllowedProvider(String provider) {
        if (provider == null) {
            return false;
        }
        String p = provider.toLowerCase(Locale.ROOT);
        if (BASE_PROVIDER_SET.contains(p)) {
            return true;
        }
        // 自定义运行时：custom_<base>_<hash8>
        if (p.startsWith("custom_")) {
            return BASE_PROVIDER_SET.contains(baseProvider(p));
        }
        return false;
    }

    /** 自定义运行时解析基础协议；内置原样返回。 */
    public static String baseProvider(String provider) {
        if (provider == null) {
            return "";
        }
        String p = provider.toLowerCase(Locale.ROOT);
        if (!p.startsWith("custom_")) {
            return p;
        }
        String rest = p.substring("custom_".length());
        for (String base : BASE_PROVIDERS_BY_LEN) {
            if (rest.startsWith(base + "_")) {
                return base;
            }
        }
        return p;
    }

    /** 供测试与文档列出支持的基础协议。 */
    public static List<String> allowedBaseProviders() {
        return BASE_PROVIDERS;
    }
}
