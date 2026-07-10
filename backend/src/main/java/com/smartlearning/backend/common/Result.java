package com.smartlearning.backend.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /**
     * 业务状态码
     */
    private Integer code;

    /**
     * 状态描述
     */
    private String message;

    /**
     * 业务数据
     */
    private T data;

    /**
     * 服务器时间戳（毫秒）
     */
    private Long timestamp;

    /**
     * 成功响应（带数据）
     */
    public static <T> Result<T> success(T data) {
        return success("操作成功", data);
    }

    public static <T> Result<T> success(String message, T data) {
        return Result.<T>builder()
                .code(Constants.CODE_SUCCESS)
                .message(message)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 成功响应（无数据）
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 失败响应
     */
    public static <T> Result<T> fail(Integer code, String message) {
        return Result.<T>builder()
                .code(code)
                .message(message)
                .data(null)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 失败响应（默认500）
     */
    public static <T> Result<T> fail(String message) {
        return fail(Constants.CODE_ERROR, message);
    }
}
