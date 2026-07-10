package com.smartlearning.backend.module.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlearning.backend.module.user.dto.ChangePasswordRequest;
import com.smartlearning.backend.module.auth.dto.LoginRequest;
import com.smartlearning.backend.module.auth.dto.LoginResponse;
import com.smartlearning.backend.module.auth.dto.RegisterRequest;
import com.smartlearning.backend.module.auth.dto.RegisterResponse;
import com.smartlearning.backend.module.user.dto.UpdateUserInfoRequest;
import com.smartlearning.backend.module.user.dto.UserInfoResponse;
import com.smartlearning.backend.module.user.entity.SysUser;

public interface SysUserService extends IService<SysUser> {

    /**
     * 用户注册
     */
    RegisterResponse register(RegisterRequest request);

    /**
     * 用户登录，返回登录信息
     */
    LoginResponse login(LoginRequest request);

    LoginResponse refreshToken(String refreshToken);

    /**
     * 根据ID获取用户信息
     */
    SysUser getUserById(Long userId);

    UserInfoResponse getCurrentUserInfo(Long userId);

    UserInfoResponse updateUserInfo(Long userId, UpdateUserInfoRequest request);

    void changePassword(Long userId, ChangePasswordRequest request);

    boolean verifyPassword(Long userId, String password);
}
