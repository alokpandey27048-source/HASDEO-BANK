package com.hasdeo.bank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HasdeoBankApplication {
    public static void main(String[] args) {
        SpringApplication.run(HasdeoBankApplication.class, args);
    }
}
