package org.portfolio.userservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI userServiceApiDocs(){
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement()
                        .addList("bearerAuth"))
                .info(new Info()
                        .title("User service API")
                        .description("User service API for finance-tracker")
                        .contact(getContact())
                        .license(getLicense())
                        .version("1.0.0"));
    }
    private static License getLicense(){
        License license=new License();
        license.setName("OOO pet-project made in Belarus");
        license.setUrl("---");
        return license;
    }

    private static Contact getContact(){
        Contact contact=new Contact();
        contact.setUrl("https://arrtur989.com");
        contact.setEmail("arrtur989@gmail.com");
        return contact;
    }
}
