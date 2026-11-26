package com.xsframework.module.tenant.web;

import com.xsframework.api.tenant.CreateTenantCommand;
import com.xsframework.api.tenant.TenantPort;
import com.xsframework.api.tenant.TenantStatus;
import com.xsframework.api.tenant.TenantView;
import com.xsframework.starter.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tenants")
@Tag(name = "Tenant")
public class TenantController {

    private final TenantPort tenantPort;

    public TenantController(TenantPort tenantPort) {
        this.tenantPort = tenantPort;
    }

    @PostMapping
    @Operation(
            operationId = "createTenant",
            summary = "Create a tenant",
            description = "Registers a new tenant in the control-plane registry without requiring a tenant context."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request or duplicate tenant code"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ApiResponse<TenantView> create(@Valid @RequestBody CreateTenantRequest request) {
        return ApiResponse.ok(tenantPort.create(new CreateTenantCommand(request.tenantName(), request.description())));
    }

    @GetMapping("/{tenantId}")
    @Operation(
            operationId = "getTenant",
            summary = "Get a tenant by id",
            description = "Returns one tenant from the control-plane registry."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not found")
    })
    public ApiResponse<TenantView> get(@Parameter(description = "Tenant identifier.") @PathVariable String tenantId) {
        return ApiResponse.ok(tenantPort.getById(tenantId));
    }

    @GetMapping
    @Operation(
            operationId = "listTenants",
            summary = "List tenants",
            description = "Lists all non-deleted tenants in the control-plane registry."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ApiResponse<List<TenantView>> list() {
        return ApiResponse.ok(tenantPort.list());
    }

    @PatchMapping("/{tenantId}/status")
    @Operation(
            operationId = "changeTenantStatus",
            summary = "Change tenant status",
            description = "Activates or suspends a tenant in the control-plane registry."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not found")
    })
    public ApiResponse<TenantView> changeStatus(
            @Parameter(description = "Tenant identifier.") @PathVariable String tenantId,
            @Valid @RequestBody ChangeStatusRequest request) {
        return ApiResponse.ok(tenantPort.changeStatus(tenantId, request.status()));
    }

    public record CreateTenantRequest(@NotBlank String tenantName, String description) {
    }

    public record ChangeStatusRequest(@NotNull TenantStatus status) {
    }
}
