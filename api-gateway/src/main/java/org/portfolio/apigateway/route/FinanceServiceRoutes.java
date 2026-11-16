package org.portfolio.apigateway.route;


import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.setPath;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

@Configuration
public class FinanceServiceRoutes {
    @Bean
    public RouterFunction<ServerResponse> financeRoute(@Value("${services.finance.url}") String serviceUrl){
        return route("finance-service")
                .route(RequestPredicates.path("/finance/**"),http())
                .before(uri(serviceUrl))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> financeServiceApiDocs(@Value("${services.finance.url}") String serviceUrl){
        return GatewayRouterFunctions.route("finance-service-api-docs")
                .route(RequestPredicates.path("/docs/finance-service/api-docs"),http())
                .before(uri(serviceUrl))
                .filter(setPath("/v3/api-docs"))
                .build();
    }
}
