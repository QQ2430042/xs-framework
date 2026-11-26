package com.xsframework.api.tenant;

import java.util.List;

public interface TenantPort {
    TenantView create(CreateTenantCommand command);

    TenantView getById(String tenantId);

    List<TenantView> list();

    TenantView changeStatus(String tenantId, TenantStatus status);
}
