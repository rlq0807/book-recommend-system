package com.renlq.bookrecommendsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BookRecommendSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookRecommendSystemApplication.class, args);
    }

}
