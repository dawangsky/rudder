package com.rudder.server.service;

import com.rudder.server.domain.TaskEntity;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 对 taskView 的轻量单元测试（不启 Spring）。
 */
class TaskViewUnitTest {

    @Test
    void taskView_mapsFields() {
        OrchestrationService svc = new OrchestrationService(null, null, null, null, null, null, null, null);
        TaskEntity t = new TaskEntity();
        t.setId(1L);
        t.setAgentId(2L);
        t.setStatus("queued");
        t.setTriggerSource("chat");
        t.setPrompt("hello");
        Map<String, Object> view = svc.taskView(t);
        assertEquals("1", view.get("id"));
        assertEquals("queued", view.get("status"));
        assertEquals("chat", view.get("triggerSource"));
        assertEquals("hello", view.get("prompt"));
    }
}
