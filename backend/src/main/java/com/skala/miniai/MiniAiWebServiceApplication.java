package com.skala.miniai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class MiniAiWebServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(MiniAiWebServiceApplication.class, args);
	}

}
