package com.franchiseads.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Binding only: the runner loads no application.yml and no .env, so every property is set here. */
class CorsPropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(WebCorsConfiguration.class);

	@Test
	void defaultsToBothLocalhostFormsOfTheViteDevServer() {
		contextRunner.run(context -> assertThat(context.getBean(CorsProperties.class).allowedOrigins())
			.containsExactly("http://localhost:5173", "http://127.0.0.1:5173"));
	}

	@Test
	void bindsCommaSeparatedOriginsTrimmed() {
		contextRunner.withPropertyValues("franchiseads.cors.allowed-origins=http://a.test, http://b.test:3000")
			.run(context -> assertThat(context.getBean(CorsProperties.class).allowedOrigins())
				.containsExactly("http://a.test", "http://b.test:3000"));
	}

	/**
	 * Unlike a scalar such as {@code effort}, an empty value binds to an empty list, not the default, so it stops
	 * startup instead of silently allowing the default origins.
	 */
	@Test
	void emptyValueStopsStartup() {
		contextRunner.withPropertyValues("franchiseads.cors.allowed-origins=")
			.run(context -> assertThat(context).hasFailed()
				.getFailure()
				.rootCause()
				.hasMessageContaining("franchiseads.cors.allowedOrigins")
				.hasMessageContaining("must not be empty"));
	}

	@Test
	void rejectsBlankOrigin() {
		contextRunner.withPropertyValues("franchiseads.cors.allowed-origins[0]=http://a.test",
				"franchiseads.cors.allowed-origins[1]= ")
			.run(context -> assertThat(context).hasFailed());
	}

}
