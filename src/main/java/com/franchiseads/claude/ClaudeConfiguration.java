package com.franchiseads.claude;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClaudeProperties.class)
public class ClaudeConfiguration {

	@Bean
	ClaudeClient claudeClient(ClaudeProperties properties) {
		return new ClaudeClient(properties);
	}

}
