package com.xsframework.adapter.user.http;

import com.xsframework.api.user.CreateUserCommand;
import com.xsframework.api.user.UserView;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Declarative HTTP client for the user service. This is the remote seam for UserPort:
 * the same operations the local UserService exposes over HTTP.
 */
@HttpExchange("/api/v1/users")
public interface UserHttpExchange {

    @PostExchange
    RemoteResponse<UserView> create(@RequestBody CreateUserCommand command);

    @GetExchange("/{userId}")
    RemoteResponse<UserView> get(@PathVariable String userId);
}
