package com.example.dbmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class DbManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DbManagerApplication.class, args);
    }
}
