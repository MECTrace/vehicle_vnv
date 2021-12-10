package com.penta.vehiclevnv.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class AppCommandLineRunner implements CommandLineRunner {

    @Override
    public void run(String... args) throws Exception {
        log.info("Application start .... ");
        LocalDateTime now = LocalDateTime.now();
        log.info("Application start time :: {}",now);
    }
}
