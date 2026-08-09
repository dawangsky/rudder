package com.rudder.server.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IssueStatusesTest {

    @Test
    void normalize_aliasesDoingToInProgress() {
        assertEquals(IssueStatuses.IN_PROGRESS, IssueStatuses.normalize("doing"));
        assertEquals(IssueStatuses.IN_PROGRESS, IssueStatuses.normalize("in_progress"));
        assertEquals(IssueStatuses.BACKLOG, IssueStatuses.normalize("planned"));
        assertEquals(IssueStatuses.IN_REVIEW, IssueStatuses.normalize("review"));
    }

    @Test
    void requireValid_rejectsUnknown() {
        assertThrows(IllegalArgumentException.class, () -> IssueStatuses.requireValid("bogus"));
        assertEquals(IssueStatuses.TODO, IssueStatuses.requireValid("todo"));
    }
}
