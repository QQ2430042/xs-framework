package com.xsframework.app.user;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
        "com.xsframework.common",
        "com.xsframework.api",
        "com.xsframework.starter",
        "com.xsframework.module.user",
        "com.xsframework.app.user"
})
@EntityScan("com.xsframework.module.user")
@EnableJpaRepositories("com.xsframework.module.user")
@OpenAPIDefinition(info = @Info(
        title = "XS User Service API",
        version = "1.0.0",
        description = "Tenant-scoped user service API contract."
))
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
