package com.dbmanager.config;

import com.dbmanager.util.AesEncryptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public AesEncryptor aesEncryptor() {
        return new AesEncryptor();
    }
}
