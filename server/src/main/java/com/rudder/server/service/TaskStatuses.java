package com.rudder.server.service;

import java.util.Locale;
import java.util.Set;

/**
 * Task 状态机辅助：终态判断与合法状态集合。
 * 状态流转由 OrchestrationService 负责写入，本类只做纯函数校验供单测与业务共用。
 */
public final class TaskStatuses {

    public static final String QUEUED = "queued";
    public static final String DISPATCHED = "dispatched";
    public static final String RUNNING = "running";
    public static final String COMPLETED = "completed";
    public static final String FAILED = "failed";
    public static final String CANCELLED = "cancelled";

    private static final Set<String> ALL = Set.of(
            QUEUED, DISPATCHED, RUNNING, COMPLETED, FAILED, CANCELLED
    );

    private static final Set<String> TERMINAL = Set.of(COMPLETED, FAILED, CANCELLED);

    private TaskStatuses() {
    }

    public static boolean isKnown(String status) {
        return status != null && ALL.contains(status.toLowerCase(Locale.ROOT));
    }

    /** 已结束、不可再取消。 */
    public static boolean isTerminal(String status) {
        return status != null && TERMINAL.contains(status.toLowerCase(Locale.ROOT));
    }

    /** 可被 Daemon claim 的状态。 */
    public static boolean isClaimable(String status) {
        return QUEUED.equalsIgnoreCase(status);
    }
}
