package br.com.datalume;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DatalumeApplication {

    public static void main(String[] args) {
        SpringApplication.run(DatalumeApplication.class, args);
    }
}
