package com.smartlearning.backend.module.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;

    private String refreshToken;

    private Long userId;

    private String username;

    private Integer role;

    private String realName;
}
