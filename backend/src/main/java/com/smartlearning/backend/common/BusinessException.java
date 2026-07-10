package com.smartlearning.backend.common;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    /**
     * 异常状态码
     */
    private final Integer code;

    /**
     * 异常信息
     */
    private final String message;

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public BusinessException(String message) {
        this(Constants.CODE_ERROR, message);
    }
}