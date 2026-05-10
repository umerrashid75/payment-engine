package com.coreissuer.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"com.coreissuer.admin", "com.coreissuer.common"})
@EntityScan(basePackages = "com.coreissuer.common.domain")
@EnableJpaRepositories(basePackages = "com.coreissuer.common.repository")
public class AdminApplication {
    public static void main(String[] args) {
        // Run on 8081 to avoid conflict with core-api
        System.setProperty("server.port", "8081");
        SpringApplication.run(AdminApplication.class, args);
    }
}
