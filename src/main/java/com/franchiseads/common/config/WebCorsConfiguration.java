package com.franchiseads.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS for every MVC endpoint, for a UI on another origin (ADR-037). A request from an origin that isn't allowed, or a
 * preflight asking for another method or header, gets Spring's plain-text 403 before any controller runs. Actuator
 * endpoints aren't covered.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsProperties.class)
public class WebCorsConfiguration implements WebMvcConfigurer {

	private final CorsProperties properties;

	public WebCorsConfiguration(CorsProperties properties) {
		this.properties = properties;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
			.allowedOrigins(properties.allowedOrigins().toArray(String[]::new))
			.allowedMethods("GET", "POST", "OPTIONS")
			.allowedHeaders("Content-Type")
			// The UI reads a created resource's URL from Location; browsers hide it unless exposed.
			.exposedHeaders("Location")
			.allowCredentials(false);
	}

}
