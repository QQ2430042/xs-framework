package com.xsframework.api.tenant;

import com.xsframework.common.DomainEvent;

import java.time.Instant;

public record TenantStatusChangedEvent(
        String eventId,
        String tenantId,
        Instant occurredAt,
        TenantStatus from,
        TenantStatus to
) implements DomainEvent {
}
