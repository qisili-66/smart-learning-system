package com.smartlearning.backend.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import java.util.Objects;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 捕获自定义业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        return ResponseEntity.status(resolveStatus(e.getCode())).body(Result.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = Objects.requireNonNull(e.getBindingResult().getFieldError()).getDefaultMessage();
        return ResponseEntity.badRequest().body(Result.fail(Constants.CODE_BAD_REQUEST, message));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(BindException e) {
        String message = Objects.requireNonNull(e.getBindingResult().getFieldError()).getDefaultMessage();
        return ResponseEntity.badRequest().body(Result.fail(Constants.CODE_BAD_REQUEST, message));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest().body(Result.fail(Constants.CODE_BAD_REQUEST, e.getParameterName() + "不能为空"));
    }

    /**
     * 捕获系统级异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        // 控制台打印异常栈，便于排查问题
        log.error("系统异常: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(Constants.CODE_ERROR, "系统繁忙，请稍后再试"));
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDeniedException(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.fail(Constants.CODE_FORBIDDEN, "无权限访问该资源"));
    }

    private HttpStatus resolveStatus(Integer code) {
        if (Constants.CODE_BAD_REQUEST.equals(code)) {
            return HttpStatus.BAD_REQUEST;
        }
        if (Constants.CODE_UNAUTHORIZED.equals(code)) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (Constants.CODE_FORBIDDEN.equals(code)) {
            return HttpStatus.FORBIDDEN;
        }
        if (Constants.CODE_NOT_FOUND.equals(code)) {
            return HttpStatus.NOT_FOUND;
        }
        if (Constants.CODE_CONFLICT.equals(code)) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
