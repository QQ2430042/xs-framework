package com.xsframework.starter.web;

import com.xsframework.common.ErrorCode;

public record ApiResponse<T>(String code, String message, T data, String traceId) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("0", "OK", data, null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.code(), message, null, null);
    }
}
