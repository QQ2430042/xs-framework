package com.xsframework.common;

public record ErrorCode(String code, String message) {
    public static final ErrorCode NOT_FOUND = new ErrorCode("NOT_FOUND", "Resource not found");
    public static final ErrorCode TENANT_REQUIRED = new ErrorCode("TENANT_REQUIRED", "Tenant context is required");
    public static final ErrorCode VALIDATION_ERROR = new ErrorCode("VALIDATION_ERROR", "Validation failed");
    public static final ErrorCode INTERNAL_ERROR = new ErrorCode("INTERNAL_ERROR", "Internal server error");
    public static final ErrorCode TENANT_CODE_EXISTS = new ErrorCode("TENANT_CODE_EXISTS", "Tenant code already exists");
}
