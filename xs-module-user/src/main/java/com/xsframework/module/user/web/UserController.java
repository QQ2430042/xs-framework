package com.xsframework.module.user.web;

import com.xsframework.api.user.CreateUserCommand;
import com.xsframework.api.user.UserPort;
import com.xsframework.api.user.UserView;
import com.xsframework.starter.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User")
public class UserController {

    private final UserPort userPort;

    public UserController(UserPort userPort) {
        this.userPort = userPort;
    }

    @PostMapping
    @Operation(
            operationId = "createUser",
            summary = "Create a user",
            description = "Creates one user in the tenant resolved from the request context."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request or missing tenant context"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ApiResponse<UserView> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(userPort.create(new CreateUserCommand(request.username(), request.displayName())));
    }

    @GetMapping("/{userId}")
    @Operation(
            operationId = "getUser",
            summary = "Get a user by id",
            description = "Returns one user visible to the current tenant."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request or missing tenant context"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not found")
    })
    public ApiResponse<UserView> get(@Parameter(description = "User identifier.") @PathVariable String userId) {
        return ApiResponse.ok(userPort.getById(userId));
    }

    public record CreateUserRequest(@NotBlank String username, String displayName) {
    }
}
