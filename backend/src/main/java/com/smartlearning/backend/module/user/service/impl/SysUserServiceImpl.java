package com.smartlearning.backend.module.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlearning.backend.common.BusinessException;
import com.smartlearning.backend.common.Constants;
import com.smartlearning.backend.module.user.dto.ChangePasswordRequest;
import com.smartlearning.backend.module.auth.dto.LoginRequest;
import com.smartlearning.backend.module.auth.dto.LoginResponse;
import com.smartlearning.backend.module.auth.dto.RegisterRequest;
import com.smartlearning.backend.module.auth.dto.RegisterResponse;
import com.smartlearning.backend.module.user.dto.UpdateUserInfoRequest;
import com.smartlearning.backend.module.user.dto.UserInfoResponse;
import com.smartlearning.backend.module.user.entity.SysUser;
import com.smartlearning.backend.module.user.mapper.SysUserMapper;
import com.smartlearning.backend.module.user.service.SysUserService;
import com.smartlearning.backend.utils.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public SysUserServiceImpl(PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public RegisterResponse register(RegisterRequest request) {
        long count = lambdaQuery().eq(SysUser::getUsername, request.getUsername()).count();
        if (count > 0) {
            throw new BusinessException(Constants.CODE_CONFLICT, "username already exists");
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setGrade(request.getGrade());
        user.setPhone(request.getPhone());
        user.setRole(Constants.ROLE_STUDENT);
        user.setStatus(Constants.STATUS_NORMAL);
        save(user);
        return new RegisterResponse(user.getUserId(), user.getUsername());
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        SysUser user = lambdaQuery().eq(SysUser::getUsername, request.getUsername()).one();
        PasswordMatch passwordMatch = matchPassword(request.getPassword(), user == null ? null : user.getPassword());
        if (user == null || !passwordMatch.matched()) {
            throw new BusinessException(Constants.CODE_BAD_REQUEST, "username or password is incorrect");
        }
        if (Constants.STATUS_DISABLED.equals(user.getStatus())) {
            throw new BusinessException(Constants.CODE_FORBIDDEN, "account is disabled");
        }
        if (passwordMatch.legacyMd5()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            updateById(user);
        }
        return buildLoginResponse(user);
    }

    @Override
    public LoginResponse refreshToken(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new BusinessException(Constants.CODE_UNAUTHORIZED, "token has expired, please login again");
        }
        Long userId = jwtUtil.getUserId(refreshToken);
        SysUser user = getRequiredUser(userId);
        return buildLoginResponse(user);
    }

    @Override
    public SysUser getUserById(Long userId) {
        return getById(userId);
    }

    @Override
    public UserInfoResponse getCurrentUserInfo(Long userId) {
        return toUserInfoResponse(getRequiredUser(userId));
    }

    @Override
    public UserInfoResponse updateUserInfo(Long userId, UpdateUserInfoRequest request) {
        SysUser user = getRequiredUser(userId);
        if (request.getRealName() != null) {
            user.setRealName(request.getRealName());
        }
        if (request.getGrade() != null) {
            user.setGrade(request.getGrade());
        }
        if (request.getSubject() != null) {
            user.setSubject(request.getSubject());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        updateById(user);
        return toUserInfoResponse(user);
    }

    @Override
    public void changePassword(Long userId, ChangePasswordRequest request) {
        SysUser user = getRequiredUser(userId);
        if (!matchPassword(request.getOldPassword(), user.getPassword()).matched()) {
            throw new BusinessException(Constants.CODE_BAD_REQUEST, "old password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        updateById(user);
    }

    @Override
    public boolean verifyPassword(Long userId, String password) {
        if (!StringUtils.hasText(password)) {
            return false;
        }
        SysUser user = getById(userId);
        return user != null && matchPassword(password, user.getPassword()).matched();
    }

    private PasswordMatch matchPassword(String rawPassword, String storedPassword) {
        if (!StringUtils.hasText(rawPassword) || !StringUtils.hasText(storedPassword)) {
            return new PasswordMatch(false, false);
        }
        if (isBcryptHash(storedPassword) && passwordEncoder.matches(rawPassword, storedPassword)) {
            return new PasswordMatch(true, false);
        }
        String md5 = DigestUtils.md5DigestAsHex(rawPassword.getBytes(StandardCharsets.UTF_8));
        boolean legacyMatched = md5.equalsIgnoreCase(storedPassword);
        return new PasswordMatch(legacyMatched, legacyMatched);
    }

    private boolean isBcryptHash(String password) {
        return password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$");
    }

    private LoginResponse buildLoginResponse(SysUser user) {
        String token = jwtUtil.generateToken(user.getUserId(), user.getRole());
        return new LoginResponse(token, user.getUserId(), user.getUsername(), user.getRole(), safe(user.getRealName()));
    }

    private SysUser getRequiredUser(Long userId) {
        SysUser user = getById(userId);
        if (user == null) {
            throw new BusinessException(Constants.CODE_NOT_FOUND, "user not found");
        }
        return user;
    }

    private UserInfoResponse toUserInfoResponse(SysUser user) {
        String createTime = user.getCreateTime() == null ? "" : DATE_TIME_FORMATTER.format(user.getCreateTime());
        return new UserInfoResponse(
                user.getUserId(),
                safe(user.getUsername()),
                safe(user.getRealName()),
                user.getRole(),
                safe(user.getGrade()),
                safe(user.getSubject()),
                safe(user.getPhone()),
                createTime
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record PasswordMatch(boolean matched, boolean legacyMd5) {
    }
}
