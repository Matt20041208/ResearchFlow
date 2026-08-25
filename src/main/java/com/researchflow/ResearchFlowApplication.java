package com.researchflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ResearchFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(ResearchFlowApplication.class, args);
    }
}
