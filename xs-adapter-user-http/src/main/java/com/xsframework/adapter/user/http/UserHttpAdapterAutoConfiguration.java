package com.xsframework.adapter.user.http;

import com.xsframework.api.user.UserPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Registers the HTTP-backed UserPort only when explicitly switched to remote mode.
 * In local mode (default) the module's own UserService provides the port.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "xs.adapters.user", name = "mode", havingValue = "remote")
public class UserHttpAdapterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RestClient userRestClient(@Value("${xs.adapters.user.base-url:http://127.0.0.1:18081}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestInterceptor(new TenantHeaderInterceptor())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public UserHttpExchange userHttpExchange(RestClient userRestClient) {
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(userRestClient))
                .build();
        return factory.createClient(UserHttpExchange.class);
    }

    @Bean
    @ConditionalOnMissingBean(UserPort.class)
    public UserPort remoteUserPort(UserHttpExchange exchange) {
        return new RemoteUserPort(exchange);
    }
}
