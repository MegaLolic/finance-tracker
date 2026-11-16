package org.portfolio.apigateway.route;

import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;


import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.setPath;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

@Configuration
public class UserServiceRoutes {

    @Bean
    public RouterFunction<ServerResponse> userRoute(@Value("${services.user.url}") String serviceUrl){
        return route("user-service")
                .route(RequestPredicates.path("/users/**"),http())
                .before(uri(serviceUrl))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> userAuthRoute(@Value("${services.user.url}") String serviceUrl){
        return route("user-service-auth")
                .route(RequestPredicates.path("/auth/**"),http())
                .before(uri(serviceUrl))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> userServiceApiDocs(@Value("${services.user.url}") String serviceUrl){
        return GatewayRouterFunctions.route("user-service-api-docs")
                .route(RequestPredicates.path("/docs/user-service/api-docs"),http())
                .before(uri(serviceUrl))
                .filter(setPath("/v3/api-docs"))
                .build();
    }
}
