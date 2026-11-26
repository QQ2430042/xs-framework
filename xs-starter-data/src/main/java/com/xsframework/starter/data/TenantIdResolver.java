package com.xsframework.starter.data;

import com.xsframework.common.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

public class TenantIdResolver implements CurrentTenantIdentifierResolver<String> {

    public static final String SENTINEL = "__none__";

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenantId = TenantContext.get();
        return tenantId == null || tenantId.isBlank() ? SENTINEL : tenantId;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
