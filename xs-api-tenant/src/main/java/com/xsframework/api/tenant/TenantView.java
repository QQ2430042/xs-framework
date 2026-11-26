package com.xsframework.api.tenant;

public record TenantView(
        String tenantId,
        String tenantCode,
        String tenantName,
        String description,
        TenantStatus status
) {
}
