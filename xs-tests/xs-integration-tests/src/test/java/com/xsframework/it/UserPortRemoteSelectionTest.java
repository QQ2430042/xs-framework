package com.xsframework.it;

import com.xsframework.adapter.user.http.RemoteUserPort;
import com.xsframework.api.user.UserPort;
import com.xsframework.app.monolith.MonolithApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Remote mode: the HTTP adapter implements the port, the local service steps aside.
 * This test only verifies bean selection; the wire call is covered by the deploy demo.
 */
@SpringBootTest(
        classes = MonolithApplication.class,
        properties = {
                "xs.adapters.user.mode=remote",
                "xs.adapters.user.base-url=http://127.0.0.1:18081"
        }
)
class UserPortRemoteSelectionTest {

    @Autowired
    private UserPort userPort;

    @Test
    void remoteModeUsesHttpAdapter() {
        assertInstanceOf(RemoteUserPort.class, userPort);
    }
}
