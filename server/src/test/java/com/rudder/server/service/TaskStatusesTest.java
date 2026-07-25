package com.rudder.server.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskStatusesTest {

    @Test
    void terminal_states_cannot_cancel() {
        assertTrue(TaskStatuses.isTerminal("completed"));
        assertTrue(TaskStatuses.isTerminal("failed"));
        assertTrue(TaskStatuses.isTerminal("cancelled"));
        assertFalse(TaskStatuses.isTerminal("queued"));
        assertFalse(TaskStatuses.isTerminal("running"));
        assertFalse(TaskStatuses.isTerminal("dispatched"));
    }

    @Test
    void claimable_only_queued() {
        assertTrue(TaskStatuses.isClaimable("queued"));
        assertFalse(TaskStatuses.isClaimable("running"));
        assertFalse(TaskStatuses.isClaimable("completed"));
    }

    @Test
    void known_statuses() {
        assertTrue(TaskStatuses.isKnown("queued"));
        assertTrue(TaskStatuses.isKnown("DISPATCHED"));
        assertFalse(TaskStatuses.isKnown("paused"));
        assertFalse(TaskStatuses.isKnown(null));
    }
}
