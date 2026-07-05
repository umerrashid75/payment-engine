package com.coreissuer.admin;

import org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"com.coreissuer.admin", "com.coreissuer.common"})
@EntityScan(basePackages = "com.coreissuer.common.domain")
@EnableJpaRepositories(basePackages = "com.coreissuer.common.repository")
public class AdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }

    @Bean
    public FilterRegistrationBean<StrutsPrepareAndExecuteFilter> strutsFilter() {
        FilterRegistrationBean<StrutsPrepareAndExecuteFilter> registration =
                new FilterRegistrationBean<>(new StrutsPrepareAndExecuteFilter());
        registration.addUrlPatterns("/*");
        return registration;
    }
}
