package com.xsframework.common;

public record Result<T>(boolean success, String code, String message, T data) {
    public static <T> Result<T> ok(T data) {
        return new Result<>(true, "0", "OK", data);
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(false, errorCode.code(), message, null);
    }
}
