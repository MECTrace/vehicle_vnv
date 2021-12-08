package com.penta.vehiclevnv;

import com.penta.vehiclevnv.configuration.FileInfoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableConfigurationProperties(
        {FileInfoProperties.class}
)
@SpringBootApplication
@EnableScheduling
public class VehicleVnvApplication {

    public static void main(String[] args) {
        SpringApplication.run(VehicleVnvApplication.class, args);
    }

}
