package com.smartlearning.backend.security;

import com.smartlearning.backend.common.BusinessException;
import com.smartlearning.backend.common.Constants;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException(Constants.CODE_UNAUTHORIZED, "未登录，请先登录");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long userId) {
            return userId;
        }
        if (principal instanceof String value) {
            try {
                return Long.valueOf(value);
            } catch (NumberFormatException ignored) {
                throw new BusinessException(Constants.CODE_UNAUTHORIZED, "登录状态无效，请重新登录");
            }
        }
        throw new BusinessException(Constants.CODE_UNAUTHORIZED, "登录状态无效，请重新登录");
    }
}
