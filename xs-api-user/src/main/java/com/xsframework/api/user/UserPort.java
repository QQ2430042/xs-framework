package com.xsframework.api.user;

public interface UserPort {
    UserView create(CreateUserCommand command);

    UserView getById(String userId);
}
