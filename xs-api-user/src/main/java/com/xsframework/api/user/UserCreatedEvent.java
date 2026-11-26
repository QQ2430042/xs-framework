package com.xsframework.api.user;

import com.xsframework.common.DomainEvent;

import java.time.Instant;

public record UserCreatedEvent(
        String eventId,
        String tenantId,
        Instant occurredAt,
        String userId,
        String userCode
) implements DomainEvent {
}
