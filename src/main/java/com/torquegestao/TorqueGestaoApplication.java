package com.torquegestao;

import com.torquegestao.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableScheduling
public class TorqueGestaoApplication {

    public static void main(String[] args) {
        SpringApplication.run(TorqueGestaoApplication.class, args);
    }
}
