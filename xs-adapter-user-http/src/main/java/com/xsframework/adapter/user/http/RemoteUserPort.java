package com.xsframework.adapter.user.http;

import com.xsframework.api.user.CreateUserCommand;
import com.xsframework.api.user.UserPort;
import com.xsframework.api.user.UserView;
import com.xsframework.common.BusinessException;
import com.xsframework.common.ErrorCode;

/**
 * Remote implementation of UserPort. Business code injects UserPort and never knows
 * whether it is backed by the local service or this HTTP adapter.
 */
public class RemoteUserPort implements UserPort {

    private final UserHttpExchange exchange;

    public RemoteUserPort(UserHttpExchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public UserView create(CreateUserCommand command) {
        return unwrap(exchange.create(command));
    }

    @Override
    public UserView getById(String userId) {
        return unwrap(exchange.get(userId));
    }

    private UserView unwrap(RemoteResponse<UserView> response) {
        if (response == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Empty response from user service");
        }
        if (response.isSuccess()) {
            if (response.data() == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "User service returned no data");
            }
            return response.data();
        }
        ErrorCode code = new ErrorCode(response.code(), response.message());
        throw new BusinessException(code, response.message());
    }
}
