package com.rudder.server.service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Issue 看板状态：待规划 → 待办 → 进行中 → 审核中 → 已完成。
 * 兼容旧值 doing → in_progress。
 */
public final class IssueStatuses {

    public static final String BACKLOG = "backlog";
    public static final String TODO = "todo";
    public static final String IN_PROGRESS = "in_progress";
    public static final String IN_REVIEW = "in_review";
    public static final String DONE = "done";

    private static final Set<String> ALL = Set.of(BACKLOG, TODO, IN_PROGRESS, IN_REVIEW, DONE);

    private static final Map<String, String> ALIASES = Map.of(
            "doing", IN_PROGRESS,
            "active", IN_PROGRESS,
            "in-progress", IN_PROGRESS,
            "review", IN_REVIEW,
            "planned", BACKLOG,
            "planning", BACKLOG
    );

    private IssueStatuses() {
    }

    /** 规范化并校验；非法则抛 IllegalArgumentException。 */
    public static String requireValid(String raw) {
        String n = normalize(raw);
        if (n == null || !ALL.contains(n)) {
            throw new IllegalArgumentException("无效的 Issue 状态: " + raw);
        }
        return n;
    }

    /** 规范化状态；未知则原样小写返回（读库兼容）。 */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return ALIASES.getOrDefault(s, s);
    }

    public static boolean isKnown(String status) {
        String n = normalize(status);
        return n != null && ALL.contains(n);
    }
}
