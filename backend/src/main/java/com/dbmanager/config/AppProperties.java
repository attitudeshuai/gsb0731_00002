package com.dbmanager.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Aes aes = new Aes();
    private Query query = new Query();
    private Export export = new Export();

    @Getter
    @Setter
    public static class Aes {
        private String secretKey;
    }

    @Getter
    @Setter
    public static class Query {
        private int maxRows = 10000;
        private int historyLimit = 200;
    }

    @Getter
    @Setter
    public static class Export {
        private int batchSize = 1000;
    }
}
