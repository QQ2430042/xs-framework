package com.xsframework.app.monolith;

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
        "com.xsframework.module",
        "com.xsframework.app.monolith"
})
@EntityScan("com.xsframework.module")
@EnableJpaRepositories("com.xsframework.module")
@OpenAPIDefinition(info = @Info(
        title = "XS Framework API",
        version = "1.0.0",
        description = "Tenant-scoped service API contract."
))
public class MonolithApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonolithApplication.class, args);
    }
}
