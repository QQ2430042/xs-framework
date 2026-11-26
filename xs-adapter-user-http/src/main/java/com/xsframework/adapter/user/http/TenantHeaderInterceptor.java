package com.xsframework.adapter.user.http;

import com.xsframework.common.TenantContext;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Propagates the current tenant to the remote service via the same header the
 * TenantContextFilter understands. No implicit ThreadLocal inheritance: the
 * caller must have an active context.
 */
public class TenantHeaderInterceptor implements ClientHttpRequestInterceptor {

    public static final String TENANT_HEADER = "X-Tenant-Id";

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String tenantId = TenantContext.get();
        if (tenantId != null && !tenantId.isBlank()) {
            request.getHeaders().set(TENANT_HEADER, tenantId);
        }
        return execution.execute(request, body);
    }
}
