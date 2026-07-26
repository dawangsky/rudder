package com.rudder.server.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorkdirResolverTest {

    @TempDir
    Path temp;

    @Test
    void sandboxPath_containsWorkspaceAndTask() {
        String dir = WorkdirResolver.sandboxWorkdir(1L, 2L);
        assertTrue(dir.contains("1"));
        assertTrue(dir.contains("2"));
        assertTrue(dir.endsWith("workdir") || dir.contains("workdir"));
    }

    @Test
    void resolve_prefersProjectLocalPath() throws Exception {
        Path p = temp.resolve("repo");
        Files.createDirectories(p);
        String cwd = WorkdirResolver.resolve(9L, 8L, p.toString());
        assertEquals(p.toAbsolutePath().normalize().toString(), cwd);
    }

    @Test
    void resolve_fallsBackToSandbox() {
        String cwd = WorkdirResolver.resolve(9L, 8L, null);
        assertTrue(cwd.contains("workdir"));
    }

    @Test
    void validate_rejectsMissingPath() {
        assertThrows(IllegalArgumentException.class, () -> WorkdirResolver.validateLocalPath("/tmp/rudder-not-exists-xyz"));
    }

    @Test
    void provider_allowlist() {
        assertTrue(WorkdirResolver.isAllowedProvider("cursor"));
        assertTrue(WorkdirResolver.isAllowedProvider("stub"));
        assertTrue(WorkdirResolver.isAllowedProvider("opencode"));
        assertTrue(WorkdirResolver.isAllowedProvider("codebuddy"));
        assertTrue(WorkdirResolver.isAllowedProvider("qwen"));
        assertTrue(WorkdirResolver.isAllowedProvider("kimi"));
        assertTrue(WorkdirResolver.isAllowedProvider("custom_opencode_abcd1234"));
        assertTrue(WorkdirResolver.isAllowedProvider("custom_claude_code_abcd1234"));
        assertEquals("claude_code", WorkdirResolver.baseProvider("custom_claude_code_abcd1234"));
        assertEquals("opencode", WorkdirResolver.baseProvider("custom_opencode_abcd1234"));
        assertFalse(WorkdirResolver.isAllowedProvider("unknown"));
        assertFalse(WorkdirResolver.isAllowedProvider("custom_unknown_abcd1234"));
    }
}
