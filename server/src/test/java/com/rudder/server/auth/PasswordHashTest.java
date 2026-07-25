package com.rudder.server.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

/** 密码哈希行为单测（与 AuthService 使用同一 BCrypt 实现）。 */
class PasswordHashTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void bcrypt_matches_same_password() {
        String hash = encoder.encode("secret12");
        assertTrue(encoder.matches("secret12", hash));
        assertFalse(encoder.matches("wrong", hash));
    }

    @Test
    void bcrypt_hashes_differ_per_encode() {
        String a = encoder.encode("secret12");
        String b = encoder.encode("secret12");
        assertNotEquals(a, b);
        assertTrue(encoder.matches("secret12", a));
        assertTrue(encoder.matches("secret12", b));
    }
}
