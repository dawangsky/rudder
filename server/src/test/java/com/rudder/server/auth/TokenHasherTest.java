package com.rudder.server.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenHasherTest {

    @Test
    void rawToken_isUrlSafeAndHashedConsistently() {
        String raw = TokenHasher.newRawToken();
        assertFalse(raw.isBlank());
        assertFalse(raw.contains("+"));
        assertFalse(raw.contains("/"));
        String h1 = TokenHasher.sha256Hex(raw);
        String h2 = TokenHasher.sha256Hex(raw);
        assertEquals(64, h1.length());
        assertEquals(h1, h2);
        assertNotEquals(h1, TokenHasher.sha256Hex(raw + "x"));
    }
}
