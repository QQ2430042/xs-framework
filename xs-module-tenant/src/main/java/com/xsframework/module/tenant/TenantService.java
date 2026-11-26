package com.xsframework.module.tenant;

import com.xsframework.api.tenant.CreateTenantCommand;
import com.xsframework.api.tenant.TenantPort;
import com.xsframework.api.tenant.TenantStatus;
import com.xsframework.api.tenant.TenantView;
import com.xsframework.common.BusinessException;
import com.xsframework.common.ErrorCode;
import com.xsframework.common.Ids;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TenantService implements TenantPort {

    private final TenantRepository repository;

    public TenantService(TenantRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TenantView create(CreateTenantCommand command) {
        String tenantCode = Ids.businessCode("TN");
        if (repository.findByTenantCode(tenantCode).isPresent()) {
            throw new BusinessException(ErrorCode.TENANT_CODE_EXISTS);
        }
        TenantEntity entity = TenantEntity.create(tenantCode, command.tenantName(), command.description());
        TenantEntity saved = repository.saveAndFlush(entity);
        return toView(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TenantView getById(String tenantId) {
        return repository.findById(tenantId)
                .map(this::toView)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Tenant not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TenantView> list() {
        return repository.findAll().stream().map(this::toView).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TenantView changeStatus(String tenantId, TenantStatus status) {
        TenantEntity entity = repository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Tenant not found"));
        entity.changeStatus(status);
        return toView(repository.saveAndFlush(entity));
    }

    private TenantView toView(TenantEntity entity) {
        return new TenantView(
                entity.getId(),
                entity.getTenantCode(),
                entity.getTenantName(),
                entity.getDescription(),
                entity.getStatus()
        );
    }
}
