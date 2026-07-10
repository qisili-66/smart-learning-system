package com.smartlearning.backend.module.auth.controller;

import com.smartlearning.backend.common.Result;
import com.smartlearning.backend.module.auth.dto.LoginRequest;
import com.smartlearning.backend.module.auth.dto.LoginResponse;
import com.smartlearning.backend.module.auth.dto.RefreshTokenRequest;
import com.smartlearning.backend.module.auth.dto.RegisterRequest;
import com.smartlearning.backend.module.auth.dto.RegisterResponse;
import com.smartlearning.backend.module.user.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证授权模块")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final SysUserService sysUserService;

    public AuthController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success("注册成功", sysUserService.register(request));
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(sysUserService.login(request));
    }

    @Operation(summary = "刷新Token")
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.success(sysUserService.refreshToken(request.getRefreshToken()));
    }

    @Operation(summary = "Logout")
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success();
    }
}
