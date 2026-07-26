package com.rudder.server.service;

import java.util.List;

/**
 * 内置运行时协议种子（与 Daemon detect.Catalog / 前端 DEFAULT_PROVIDERS 对齐）。
 * 工作区创建时写入 rb_workspace_protocol；之后以库为准。
 */
public final class BuiltinProtocols {

    public record Spec(
            String code,
            String label,
            String shortLabel,
            List<String> bins,
            String commandHint,
            String region
    ) {}

    public static final List<Spec> ALL = List.of(
            new Spec("claude_code", "Claude Code", "Claude", List.of("claude", "claude-code"),
                    "例如：claude -p \"{prompt}\"", "intl"),
            new Spec("cursor", "Cursor", "Cursor", List.of("cursor", "cursor-agent", "agent"),
                    "例如：agent \"{prompt}\"", "intl"),
            new Spec("codex", "Codex", "Codex", List.of("codex"),
                    "例如：codex exec \"{prompt}\"", "intl"),
            new Spec("opencode", "OpenCode", "OpenCode", List.of("opencode"),
                    "例如：opencode run \"{prompt}\"", "intl"),
            new Spec("gemini", "Gemini CLI", "Gemini", List.of("gemini"),
                    "例如：gemini -p \"{prompt}\"", "intl"),
            new Spec("copilot", "GitHub Copilot", "Copilot", List.of("copilot"),
                    "例如：copilot -p \"{prompt}\"", "intl"),
            new Spec("aider", "Aider", "Aider", List.of("aider"),
                    "例如：aider --message \"{prompt}\" --yes-always", "intl"),
            new Spec("goose", "Goose", "Goose", List.of("goose"),
                    "例如：goose run \"{prompt}\"", "intl"),
            new Spec("codebuddy", "CodeBuddy", "CodeBuddy", List.of("codebuddy"),
                    "例如：codebuddy -p \"{prompt}\"", "cn"),
            new Spec("qwen", "Qwen Code", "Qwen", List.of("qwen", "qwen-code"),
                    "例如：qwen -p \"{prompt}\"", "cn"),
            new Spec("kimi", "Kimi Code", "Kimi", List.of("kimi", "kimi-code"),
                    "例如：kimi -p \"{prompt}\"", "cn"),
            new Spec("qoder", "Qoder", "Qoder", List.of("qoder", "qodercli"),
                    "例如：qoder -p \"{prompt}\"", "cn"),
            new Spec("traecli", "Trae CLI", "Trae", List.of("traecli", "trae", "trae-cli"),
                    "例如：traecli -p \"{prompt}\"", "cn"),
            new Spec("kiro", "Kiro", "Kiro", List.of("kiro"),
                    "例如：kiro -p \"{prompt}\"", "intl"),
            new Spec("grok", "Grok", "Grok", List.of("grok"),
                    "例如：grok -p \"{prompt}\"", "intl"),
            new Spec("hermes", "Hermes", "Hermes", List.of("hermes"),
                    "例如：hermes -p \"{prompt}\"", "intl"),
            new Spec("pi", "Pi", "Pi", List.of("pi"),
                    "例如：pi -p \"{prompt}\"", "intl"),
            new Spec("openclaw", "OpenClaw", "OpenClaw", List.of("openclaw"),
                    "例如：openclaw -p \"{prompt}\"", "intl"),
            new Spec("antigravity", "Antigravity", "Antigravity", List.of("antigravity"),
                    "例如：antigravity -p \"{prompt}\"", "intl"),
            new Spec("deveco", "DevEco", "DevEco", List.of("deveco", "hvigorw"),
                    "例如：deveco -p \"{prompt}\"", "cn"),
            new Spec("stub", "Stub", "Stub", List.of(),
                    "无需本机 CLI（测试用）", "test")
    );

    private BuiltinProtocols() {
    }
}
