package com.xsframework.app.tenant;

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
        "com.xsframework.module.tenant",
        "com.xsframework.app.tenant"
})
@EntityScan("com.xsframework.module.tenant")
@EnableJpaRepositories("com.xsframework.module.tenant")
@OpenAPIDefinition(info = @Info(
        title = "XS Tenant Service API",
        version = "1.0.0",
        description = "Control-plane tenant registry API contract."
))
public class TenantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TenantServiceApplication.class, args);
    }
}
