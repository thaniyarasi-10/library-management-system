package com.kovanlabs.librarymanagement.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = "com.kovanlabs.librarymanagement")
@EnableJpaRepositories(basePackages = "com.kovanlabs.librarymanagement.database.repository")
@EntityScan(basePackages = "com.kovanlabs.librarymanagement.database.entity")
@EnableScheduling
@EnableCaching
//@EnableAsync

public class LibraryManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(LibraryManagementApplication.class, args);
    }
}
