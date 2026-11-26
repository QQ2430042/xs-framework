package com.xsframework.it;

import com.xsframework.api.user.UserPort;
import com.xsframework.app.monolith.MonolithApplication;
import com.xsframework.module.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Default mode: the local service implements the port and no HTTP adapter is active.
 */
@SpringBootTest(classes = MonolithApplication.class)
class UserPortLocalSelectionTest {

    @Autowired
    private UserPort userPort;

    @Test
    void defaultModeUsesLocalService() {
        assertInstanceOf(UserService.class, userPort);
    }
}
