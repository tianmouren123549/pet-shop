package com.gzu.petshop.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 未捕获异常统一转为面向用户/商家的中文提示，避免堆栈、SQL 等技术信息泄露到前端。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 上传超过 Spring 配置的单文件/总大小限制。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        log.warn("upload size exceeded: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Result.error(413, "上传文件过大，请压缩图片或联系管理员放宽限制"));
    }

    /**
     * {@code @Valid} 校验失败。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        return Result.error("提交信息不完整，请检查后重试");
    }

    /**
     * JSON 反序列化失败等。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.debug("bad request body: {}", e.getMessage());
        return Result.error("数据格式不正确，请刷新页面后重试");
    }

    /**
     * 兜底：记录完整异常，仅返回固定提示。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleAny(Exception e) {
        log.error("unhandled exception", e);
        return Result.error("服务繁忙，请稍后重试");
    }
}
