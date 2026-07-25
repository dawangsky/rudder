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
        assertFalse(WorkdirResolver.isAllowedProvider("unknown"));
    }
}
