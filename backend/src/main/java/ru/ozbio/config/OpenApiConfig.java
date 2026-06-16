package ru.ozbio.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI ozbioOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ozbio — планировщик завода")
                        .description("MVP API: заказы и расписание")
                        .version("0.1.0")
                        .contact(new Contact().name("ozbio")));
    }
}
