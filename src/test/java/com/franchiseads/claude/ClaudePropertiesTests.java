package com.franchiseads.claude;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Binding only: the runner loads no application.yml and no .env, so every property is set here. */
class ClaudePropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(ClaudeConfiguration.class);

	@Test
	void bindsDocumentedDefaults() {
		contextRunner.run(context -> {
			ClaudeProperties properties = context.getBean(ClaudeProperties.class);
			assertThat(properties.baseUrl()).isEqualTo("https://api.anthropic.com");
			assertThat(properties.model()).isEqualTo("claude-haiku-5-5");
			assertThat(properties.maxTokens()).isEqualTo(4096);
			assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
			assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(30));
			assertThat(properties.maxAttempts()).isEqualTo(3);
			assertThat(properties.initialBackoff()).isEqualTo(Duration.ofMillis(500));
			assertThat(properties.maxJitter()).isEqualTo(0.25);
			assertThat(properties.retryAfterCap()).isEqualTo(Duration.ofSeconds(10));
		});
	}

	@Test
	void contextStartsWithoutApiKeyAndClientRefusesToCall() {
		contextRunner.withPropertyValues("franchiseads.claude.api-key=").run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(ClaudeProperties.class).hasApiKey()).isFalse();
			assertThatThrownBy(() -> context.getBean(ClaudeClient.class).complete("system", "user"))
				.isInstanceOf(ClaudeRequestException.class)
				.hasMessage("API key not configured");
		});
	}

	@Test
	void rejectsZeroMaxAttempts() {
		contextRunner.withPropertyValues("franchiseads.claude.max-attempts=0")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void rejectsJitterAboveOne() {
		contextRunner.withPropertyValues("franchiseads.claude.max-jitter=1.5")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void toStringMasksTheApiKey() {
		contextRunner.withPropertyValues("franchiseads.claude.api-key=sk-ant-secret-value").run(context -> {
			String text = context.getBean(ClaudeProperties.class).toString();
			assertThat(text).doesNotContain("sk-ant-secret-value").contains("apiKey=****");
		});
	}

}
