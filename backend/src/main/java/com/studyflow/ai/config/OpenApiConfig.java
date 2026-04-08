package com.studyflow.ai.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI studyFlowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("StudyFlow AI API")
                        .description("StudyFlow AI backend skeleton APIs")
                        .version("v1.0.0")
                        .contact(new Contact().name("StudyFlow AI Team").email("109443777@qq.com"))
                        .license(new License().name("Apache-2.0")));
    }
}
