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
 * 协议白名单以 {@link ProtocolService} 工作区目录为准；此处仅保留静态解析兜底。
 */
public final class WorkdirResolver {

    private static final Set<String> DENY_PREFIXES = Set.of(
            "/", "/etc", "/usr", "/bin", "/sbin", "/System", "/Library", "/private/etc"
    );

    /** 内置种子 code，用于无工作区上下文时的 custom_ 解析兜底。 */
    private static final List<String> DEFAULT_BASES_BY_LEN = BuiltinProtocols.ALL.stream()
            .map(BuiltinProtocols.Spec::code)
            .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(s -> s))
            .collect(Collectors.toList());

    private static final Set<String> DEFAULT_BASE_SET = BuiltinProtocols.ALL.stream()
            .map(BuiltinProtocols.Spec::code)
            .collect(Collectors.toUnmodifiableSet());

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

    /**
     * 静态兜底白名单（仅内置种子）。业务校验请用 {@link ProtocolService#isAllowedProvider}。
     */
    public static boolean isAllowedProvider(String provider) {
        if (provider == null) return false;
        String p = provider.toLowerCase(Locale.ROOT);
        if (DEFAULT_BASE_SET.contains(p)) return true;
        if (p.startsWith("custom_")) {
            return DEFAULT_BASE_SET.contains(baseProviderStatic(p));
        }
        return false;
    }

    public static String baseProvider(String provider) {
        return baseProviderStatic(provider);
    }

    /** 无工作区上下文时的基础协议解析。 */
    public static String baseProviderStatic(String provider) {
        if (provider == null) return "";
        String p = provider.toLowerCase(Locale.ROOT);
        if (!p.startsWith("custom_")) return p;
        String rest = p.substring("custom_".length());
        for (String base : DEFAULT_BASES_BY_LEN) {
            if (rest.startsWith(base + "_")) return base;
        }
        return p;
    }

    public static List<String> allowedBaseProviders() {
        return BuiltinProtocols.ALL.stream().map(BuiltinProtocols.Spec::code).collect(Collectors.toList());
    }
}
