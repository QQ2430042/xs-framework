package com.xsframework.common;

import java.util.function.Supplier;

public final class TenantContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(String tenantId) {
        CURRENT.set(tenantId);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static String require() {
        String tenantId = CURRENT.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new BusinessException(ErrorCode.TENANT_REQUIRED);
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static <T> T call(String tenantId, Supplier<T> body) {
        String previous = CURRENT.get();
        try {
            CURRENT.set(tenantId);
            return body.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
