package org.esoalahy.stock.core.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentation OpenAPI (cahier §6) — /swagger-ui.html. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI stockOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Stock — import Chine, stock et clients")
                        .version("lot-1-socle")
                        .description("API REST du socle : utilisateurs, catalogue, dépôts, stock, inventaires, étiquettes."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
