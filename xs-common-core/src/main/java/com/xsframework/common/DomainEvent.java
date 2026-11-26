package com.xsframework.common;

import java.time.Instant;

public interface DomainEvent {
    String eventId();

    String tenantId();

    Instant occurredAt();
}
