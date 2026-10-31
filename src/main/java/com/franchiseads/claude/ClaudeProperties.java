package com.franchiseads.claude;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Claude Messages API settings. The key and model come from {@code ANTHROPIC_API_KEY} and {@code ANTHROPIC_MODEL}
 * (see application.yml); the rest default to the values in docs/flow.md.
 *
 * @param baseUrl API root, without {@code /v1/messages}
 * @param apiKey may be blank: the app still starts, and {@link ClaudeClient} refuses to call the API (ADR-021)
 * @param model model ID sent with every request
 * @param maxTokens output cap per call; includes thinking tokens on models that think by default
 * @param effort sent as {@code output_config.effort}; {@link Effort#NONE} leaves the field out, for models that don't
 * support effort (ADR-026)
 * @param connectTimeout TCP connect timeout
 * @param readTimeout how long to wait for the response
 * @param maxAttempts total attempts per call, including the first
 * @param initialBackoff delay before the second attempt; doubles for each later one
 * @param maxJitter up to this fraction of the backoff is added at random
 * @param retryAfterCap upper bound for a {@code retry-after} delay
 */
@Validated
@ConfigurationProperties("franchiseads.claude")
public record ClaudeProperties(@DefaultValue("https://api.anthropic.com") @NotBlank String baseUrl, String apiKey,
		@DefaultValue("claude-haiku-5-5") @NotBlank String model, @DefaultValue("4096") @Min(1) int maxTokens,
		@DefaultValue("low") @NotNull Effort effort,
		@DefaultValue("3s") @NotNull Duration connectTimeout, @DefaultValue("30s") @NotNull Duration readTimeout,
		@DefaultValue("3") @Min(1) @Max(10) int maxAttempts, @DefaultValue("500ms") @NotNull Duration initialBackoff,
		@DefaultValue("0.25") @DecimalMin("0.0") @DecimalMax("1.0") double maxJitter,
		@DefaultValue("10s") @NotNull Duration retryAfterCap) {

	public boolean hasApiKey() {
		return apiKey != null && !apiKey.isBlank();
	}

	/** Never prints the key, so the properties can be logged safely. */
	@Override
	public String toString() {
		return "ClaudeProperties[baseUrl=" + baseUrl + ", apiKey=" + (hasApiKey() ? "****" : "<not set>") + ", model="
				+ model + ", maxTokens=" + maxTokens + ", effort=" + effort + ", connectTimeout=" + connectTimeout + ", readTimeout="
				+ readTimeout + ", maxAttempts=" + maxAttempts + ", initialBackoff=" + initialBackoff + ", maxJitter="
				+ maxJitter + ", retryAfterCap=" + retryAfterCap + "]";
	}

	/**
	 * Effort levels of the Messages API, plus {@link #NONE} for "don't send the field". Binding ignores case, so
	 * {@code ANTHROPIC_EFFORT=low} works. An empty value can't mean "none": Spring Boot binds it to null and then
	 * applies the default.
	 */
	public enum Effort {

		NONE, LOW, MEDIUM, HIGH, XHIGH, MAX;

		/** Whether the request carries {@code output_config.effort} at all. */
		public boolean isSent() {
			return this != NONE;
		}

		/** The value the API expects, e.g. {@code "low"}. */
		public String apiValue() {
			return name().toLowerCase(Locale.ROOT);
		}

	}

}
