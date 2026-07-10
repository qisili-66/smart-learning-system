package com.smartlearning.backend.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "username cannot be blank")
    @Size(min = 4, max = 20, message = "username length must be 4-20")
    private String username;

    @NotBlank(message = "password cannot be blank")
    @Size(min = 6, max = 20, message = "password length must be 6-20")
    private String password;

    private String realName;

    private String grade;

    private String phone;
}
