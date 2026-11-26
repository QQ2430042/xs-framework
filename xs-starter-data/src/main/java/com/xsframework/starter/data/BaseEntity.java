package com.xsframework.starter.data;

import com.xsframework.common.Ids;
import com.xsframework.common.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import org.hibernate.annotations.TenantId;

import java.time.Instant;

@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @TenantId
    @Column(name = "tenant_id", length = 36, nullable = false)
    private String tenantId;

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
        TenantContext.require();
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

    public String getTenantId() {
        return tenantId;
    }

    public long getVersion() {
        return version;
    }

    public boolean isDeletedFlag() {
        return deletedFlag;
    }
}
