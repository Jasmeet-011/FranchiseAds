package com.franchiseads.generation;

import com.franchiseads.validation.AdCopyRules;
import com.franchiseads.validation.AdCopyValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The pure generation helpers as beans. The classes themselves stay free of Spring. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GenerationProperties.class)
public class GenerationConfiguration {

	@Bean
	PromptBuilder promptBuilder(AdCopyRules rules) {
		return new PromptBuilder(rules);
	}

	@Bean
	AdCopyParser adCopyParser() {
		return new AdCopyParser();
	}

	@Bean
	AdCopyValidator adCopyValidator(AdCopyRules rules) {
		return new AdCopyValidator(rules);
	}

}
