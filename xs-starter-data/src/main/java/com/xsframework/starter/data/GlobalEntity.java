package com.xsframework.starter.data;

import com.xsframework.common.Ids;
import com.xsframework.common.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Audit base for control-plane tables that are not tenant-owned (for example the tenant registry).
 * Unlike {@link BaseEntity} it has no @TenantId and never requires a tenant context.
 */
@MappedSuperclass
public abstract class GlobalEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_by", length = 36)
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_by", length = 36)
    private String deletedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_flag", nullable = false)
    private boolean deletedFlag;

    @PrePersist
    public void onCreate() {
        if (id == null || id.isBlank()) {
            id = Ids.uuid();
        }
        createdAt = Instant.now();
        createdBy = TenantContext.get();
        deletedFlag = false;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
        updatedBy = TenantContext.get();
    }

    public void markDeleted() {
        deletedFlag = true;
        deletedAt = Instant.now();
        deletedBy = TenantContext.get();
    }

    public String getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    public boolean isDeletedFlag() {
        return deletedFlag;
    }
}
