package com.xsframework.module.tenant;

import com.xsframework.api.tenant.TenantStatus;
import com.xsframework.starter.data.GlobalEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "st_tenant")
@SQLRestriction("deleted_flag = false")
@AttributeOverride(name = "id", column = @Column(name = "tenant_id", length = 36, nullable = false))
public class TenantEntity extends GlobalEntity {

    @Column(name = "tenant_code", length = 16, nullable = false)
    private String tenantCode;

    @Column(name = "tenant_name", length = 128, nullable = false)
    private String tenantName;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private TenantStatus status;

    protected TenantEntity() {
    }

    private TenantEntity(String tenantCode, String tenantName, String description) {
        this.tenantCode = tenantCode;
        this.tenantName = tenantName;
        this.description = description;
        this.status = TenantStatus.ACTIVE;
    }

    public static TenantEntity create(String tenantCode, String tenantName, String description) {
        return new TenantEntity(tenantCode, tenantName, description);
    }

    public void changeStatus(TenantStatus status) {
        this.status = status;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public String getTenantName() {
        return tenantName;
    }

    public String getDescription() {
        return description;
    }

    public TenantStatus getStatus() {
        return status;
    }
}
