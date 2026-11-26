package com.xsframework.api.tenant;

import com.xsframework.common.DomainEvent;

import java.time.Instant;

public record TenantCreatedEvent(
        String eventId,
        String tenantId,
        Instant occurredAt,
        String tenantCode,
        String tenantName
) implements DomainEvent {
}
