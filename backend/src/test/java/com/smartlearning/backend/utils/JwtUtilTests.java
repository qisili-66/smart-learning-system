package com.smartlearning.backend.utils;

import com.smartlearning.backend.common.Constants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTests {

    private final JwtUtil jwtUtil = new JwtUtil(
            "test-secret-key-that-is-longer-than-thirty-two-bytes",
            60_000,
            120_000
    );

    @Test
    void accessAndRefreshTokensHaveDifferentPurposes() {
        String accessToken = jwtUtil.generateAccessToken(7L, Constants.ROLE_STUDENT);
        String refreshToken = jwtUtil.generateRefreshToken(7L, Constants.ROLE_STUDENT);

        assertTrue(jwtUtil.validateToken(accessToken));
        assertFalse(jwtUtil.validateRefreshToken(accessToken));
        assertTrue(jwtUtil.validateRefreshToken(refreshToken));
        assertFalse(jwtUtil.validateToken(refreshToken));
        assertEquals(7L, jwtUtil.getUserId(accessToken));
        assertEquals(Constants.ROLE_STUDENT, jwtUtil.getRole(accessToken));
    }
}
