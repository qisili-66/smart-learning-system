package com.smartlearning.backend.common;

public class Constants {

    // ========== 业务状态码 ==========
    public static final Integer CODE_SUCCESS = 200;
    public static final Integer CODE_BAD_REQUEST = 400;
    public static final Integer CODE_UNAUTHORIZED = 401;
    public static final Integer CODE_FORBIDDEN = 403;
    public static final Integer CODE_NOT_FOUND = 404;
    public static final Integer CODE_CONFLICT = 409;
    public static final Integer CODE_ERROR = 500;

    // ========== 用户角色 ==========
    public static final Integer ROLE_STUDENT = 1;
    public static final Integer ROLE_ADMIN = 2;
    public static final String SECURITY_ROLE_STUDENT = "STUDENT";
    public static final String SECURITY_ROLE_ADMIN = "ADMIN";

    // ========== 通用状态 ==========
    public static final Integer STATUS_NORMAL = 1;
    public static final Integer STATUS_DISABLED = 0;

    // ========== JWT 配置 ==========
    public static final String JWT_HEADER = "Authorization";
    public static final String JWT_PREFIX = "Bearer ";

    // ========== 分页默认值 ==========
    public static final Integer DEFAULT_PAGE_NUM = 1;
    public static final Integer DEFAULT_PAGE_SIZE = 10;
    public static final Integer MAX_PAGE_SIZE = 100;

    // ========== 错题掌握状态 ==========
    public static final Integer NOT_MASTERED = 0;
    public static final Integer IS_MASTERED = 1;

    // ========== 计划状态 ==========
    public static final Integer PLAN_RUNNING = 1;
    public static final Integer PLAN_FINISHED = 2;
    public static final Integer PLAN_TERMINATED = 3;
}
