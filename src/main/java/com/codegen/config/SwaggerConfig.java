package com.codegen.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;

import java.util.List;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger 文档配置
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .servers(List.of(new Server().url("/api/admin")))
                .info(new Info()
                        .title("平台管理 API")
                        .version("1.0")
                        .description("跨境电商商家后台-平台权限模块"));
    }
}
