package com.xsframework.starter.data;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

@AutoConfiguration
public class TenantDataAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CurrentTenantIdentifierResolver<String> currentTenantIdentifierResolver() {
        return new TenantIdResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public MultiTenantConnectionProvider<String> multiTenantConnectionProvider(DataSource dataSource) {
        return new SameDbConnectionProvider<>(dataSource);
    }

    @Bean
    @ConditionalOnProperty(prefix = "xs.tenant", name = "rls-enabled", havingValue = "true")
    public static BeanPostProcessor tenantRlsDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource dataSource && !(bean instanceof TenantRlsDataSource)) {
                    return new TenantRlsDataSource(dataSource);
                }
                return bean;
            }
        };
    }
}
