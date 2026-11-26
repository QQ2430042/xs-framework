package com.xsframework.module.user;

import com.xsframework.api.user.CreateUserCommand;
import com.xsframework.api.user.UserPort;
import com.xsframework.api.user.UserView;
import com.xsframework.common.BusinessException;
import com.xsframework.common.ErrorCode;
import com.xsframework.common.Ids;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "xs.adapters.user", name = "mode", havingValue = "local", matchIfMissing = true)
public class UserService implements UserPort {

    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserView create(CreateUserCommand command) {
        UserEntity entity = UserEntity.create(Ids.businessCode("UC"), command.username(), command.displayName());
        UserEntity saved = repository.saveAndFlush(entity);
        return toView(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public UserView getById(String userId) {
        return repository.findById(userId)
                .map(this::toView)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));
    }

    private UserView toView(UserEntity entity) {
        return new UserView(
                entity.getId(),
                entity.getTenantId(),
                entity.getUserCode(),
                entity.getUsername(),
                entity.getDisplayName()
        );
    }
}
