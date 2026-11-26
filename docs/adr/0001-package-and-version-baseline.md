# ADR 0001: Package name and version baseline

Status: accepted

## Context

Earlier notes contained three conflicting package names: `com.xs.base`, `com.xs.framework`, and `com.xsframework`. The user explicitly decided on `com.xsframework`. Version baseline must be current as of 2026-11-24 and must not use RC or SNAPSHOT artifacts.

## Decision

- Base package and Maven groupId: `com.xsframework`
- Module packages extend it: `com.xsframework.common`, `.api.<domain>`, `.starter.<capability>`, `.module.<domain>`, `.app.<assembly>`
- JDK: 25 LTS, compiled with `--release 25`
- Spring Boot: 4.2.0 GA (BOM-managed)
- Spring Cloud: 2026.0.0 GA (imported but not activated in the monolith baseline)
- Spring Data: 2026.1.0 GA; Hibernate ORM 7.4.15.Final as managed by Boot 4.2.0
- PostgreSQL: 19 GA; pgjdbc 42.7.15
- springdoc-openapi: 3.2.1
- Flyway 12.11.0, H2 2.5.252, Testcontainers 2.0.5, JUnit Jupiter 6.1.3 (all BOM-managed)

Hibernate 8 / Jakarta Persistence 4.0 is supported by Spring Data 2026.1 but is not the Boot 4.2.0 managed baseline. Do not override Hibernate manually without the full five-step dependency check.

## Consequences

All existing notes that say `com.xs.base` or `com.xs.framework` are superseded. Any dependency change must be recorded in a new ADR and verified by `dependency:tree` diff, enforcer, and `mvn clean verify`.
