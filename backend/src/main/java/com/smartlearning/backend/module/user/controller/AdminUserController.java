package com.smartlearning.backend.module.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartlearning.backend.common.BusinessException;
import com.smartlearning.backend.common.Constants;
import com.smartlearning.backend.common.PageUtils;
import com.smartlearning.backend.common.PageVO;
import com.smartlearning.backend.common.Result;
import com.smartlearning.backend.module.user.entity.SysUser;
import com.smartlearning.backend.module.user.service.SysUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "管理员用户管理模块")
@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    private final SysUserService sysUserService;
    private final PasswordEncoder passwordEncoder;

    public AdminUserController(SysUserService sysUserService, PasswordEncoder passwordEncoder) {
        this.sysUserService = sysUserService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public Result<PageVO<SysUser>> list(@RequestParam(required = false) String username,
                                        @RequestParam(required = false) Integer role,
                                        @RequestParam(required = false) Integer status,
                                        @RequestParam(required = false) Integer pageNum,
                                        @RequestParam(required = false) Integer pageSize) {
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<SysUser>()
                .like(StringUtils.hasText(username), SysUser::getUsername, username)
                .eq(role != null, SysUser::getRole, role)
                .eq(status != null, SysUser::getStatus, status)
                .orderByDesc(SysUser::getCreateTime);
        Page<SysUser> page = sysUserService.page(PageUtils.page(pageNum, pageSize), query);
        page.getRecords().forEach(user -> user.setPassword(null));
        return Result.success(PageVO.of(page));
    }

    @GetMapping("/{userId}")
    public Result<SysUser> detail(@PathVariable Long userId) {
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            throw new BusinessException(Constants.CODE_NOT_FOUND, "user not found");
        }
        user.setPassword(null);
        return Result.success(user);
    }

    @PutMapping("/{userId}/status")
    public Result<Void> status(@PathVariable Long userId, @RequestBody Map<String, Integer> request) {
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            throw new BusinessException(Constants.CODE_NOT_FOUND, "user not found");
        }
        user.setStatus(request.get("status"));
        sysUserService.updateById(user);
        return Result.success();
    }

    @PutMapping("/{userId}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long userId) {
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            throw new BusinessException(Constants.CODE_NOT_FOUND, "user not found");
        }
        user.setPassword(passwordEncoder.encode("123456"));
        sysUserService.updateById(user);
        return Result.success();
    }
}
