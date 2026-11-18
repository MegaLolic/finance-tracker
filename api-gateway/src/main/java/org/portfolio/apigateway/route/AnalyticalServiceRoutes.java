package org.portfolio.apigateway.route;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.setPath;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

@Configuration
public class AnalyticalServiceRoutes {
    @Bean
    public RouterFunction<ServerResponse> analyticalRoute(@Value("${services.analytics.url}") String serviceUrl) {
        return route("analytical-service")
                .route(RequestPredicates.path("/analytics/**"), http())
                .before(uri(serviceUrl))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> analyticalServiceApiDocs(@Value("${services.analytics.url}") String serviceUrl) {
        return route("analytical-service-api-docs")
                .route(RequestPredicates.path("/docs/analytical-service/api-docs"), http())
                .before(uri(serviceUrl))
                .filter(setPath("/v3/api-docs"))
                .build();
    }
}
