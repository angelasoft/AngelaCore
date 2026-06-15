package com.angelasoft.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.angelasoft")
@EnableJpaRepositories(basePackages = "com.angelasoft")
@EntityScan(basePackages = "com.angelasoft")
public class AngelaCoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(AngelaCoreApplication.class, args);
	}

}
