package com.qasky.qdns;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
public class QdnsApplication {

    public static void main(String[] args) {
        SpringApplication.run(QdnsApplication.class, args);
        System.out.println("Qdns启动成功，访问 http://localhost:18023/ 可进入调试页面");
    }
}
