package com.pillmate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan(basePackages = "com.pillmate.common.config")
public class PillmateApplication {

    public static void main(String[] args) {
        SpringApplication.run(PillmateApplication.class, args);
    }
}
