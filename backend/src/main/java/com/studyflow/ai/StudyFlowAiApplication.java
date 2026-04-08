package com.studyflow.ai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.studyflow.ai.mapper")
public class StudyFlowAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudyFlowAiApplication.class, args);
    }
}
