package ru.ozbio.config;

import java.util.List;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI ozbioOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("ozbio — планировщик завода")
                                .description("MVP API: заказы и расписание")
                                .version("0.1.0")
                                .contact(new Contact().name("ozbio")))
                .tags(
                        List.of(
                                new Tag().name("Machines").description("Станки"),
                                new Tag().name("Details").description("Детали"),
                                new Tag().name("Tools").description("Инструменты"),
                                new Tag().name("Shifts").description("Смены"),
                                new Tag().name("Orders").description("Заказы"),
                                new Tag().name("Schedule").description("Расписание производства")));
    }
}
