package com.pillmate.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 운영 배포 시 Swagger UI 외부 노출 주의 — 의료 API 명세 유출 위험 (medical-safety)
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pillmateOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PillMate API")
                        .version("1.0.0")
                        .description("보호자-환자 그룹 기반 스마트 복약 관리 플랫폼 API"));
    }
}
