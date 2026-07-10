package com.smartlearning.backend.module.user.controller;

import com.smartlearning.backend.common.Result;
import com.smartlearning.backend.module.user.dto.ChangePasswordRequest;
import com.smartlearning.backend.module.user.dto.UpdateUserInfoRequest;
import com.smartlearning.backend.module.user.dto.UserInfoResponse;
import com.smartlearning.backend.security.SecurityUtils;
import com.smartlearning.backend.module.user.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户信息模块")
@RestController
@RequestMapping("/users")
public class UserController {

    private final SysUserService sysUserService;

    public UserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/info")
    public Result<UserInfoResponse> getCurrentUserInfo() {
        return Result.success(sysUserService.getCurrentUserInfo(SecurityUtils.currentUserId()));
    }

    @Operation(summary = "修改个人信息")
    @PutMapping("/info")
    public Result<UserInfoResponse> updateUserInfo(@RequestBody UpdateUserInfoRequest request) {
        return Result.success(sysUserService.updateUserInfo(SecurityUtils.currentUserId(), request));
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        sysUserService.changePassword(SecurityUtils.currentUserId(), request);
        return Result.success();
    }
}
