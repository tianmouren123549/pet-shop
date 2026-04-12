package com.gzu.petshop.common;

public class Result<T> {
    private Integer code;
    private String message;
    private T data;

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("success");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 成功响应（无 data），自定义面向用户的提示文案，避免默认的 {@code success} 英文。
     *
     * @param message 提示正文，空则回退为「操作成功」
     */
    public static Result<Void> successWithMessage(String message) {
        Result<Void> result = new Result<>();
        result.setCode(200);
        String m = message == null ? "" : message.trim();
        result.setMessage(m.isEmpty() ? "操作成功" : m);
        result.setData(null);
        return result;
    }

    public static <T> Result<T> error(String message) {
        Result<T> result = new Result<>();
        result.setCode(500);
        result.setMessage(message);
        return result;
    }

    /**
     * 指定业务错误码（如 401 未授权、403 禁止访问）。
     */
    public static <T> Result<T> error(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
