package com.studiorent.tium;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TiumApplication {

    public static void main(String[] args) {
        SpringApplication.run(TiumApplication.class, args);
    }

}

