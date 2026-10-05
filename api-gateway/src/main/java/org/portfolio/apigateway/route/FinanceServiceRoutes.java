package org.portfolio.apigateway.route;


import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.context.annotation.Bean;
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
    public RouterFunction<ServerResponse> financeRoute(){
        return route("finance-service")
                .route(RequestPredicates.path("/finance/**"),http())
                .before(uri("http://localhost:8081"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> financeServiceApiDocs(){
        return GatewayRouterFunctions.route("finance-service-api-docs")
                .route(RequestPredicates.path("/docs/finance-service/api-docs"),http())
                .before(uri("http://localhost:8081"))
                .filter(setPath("/v3/api-docs"))
                .build();
    }
}
