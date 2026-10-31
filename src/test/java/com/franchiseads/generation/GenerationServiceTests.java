package com.franchiseads.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.franchiseads.claude.ClaudeClient;
import com.franchiseads.claude.ClaudeException;
import com.franchiseads.claude.ClaudeProperties;
import com.franchiseads.claude.ClaudeRequestException;
import com.franchiseads.claude.ClaudeResponse;
import com.franchiseads.claude.ClaudeTimeoutException;
import com.franchiseads.claude.ClaudeUnavailableException;
import com.franchiseads.claude.MutableClock;
import com.franchiseads.common.error.ResourceNotFoundException;
import com.franchiseads.validation.AdCopyRules;
import com.franchiseads.validation.AdCopyValidator;
import com.franchiseads.variant.AdVariant;
import com.franchiseads.variant.GeneratedVariant;
import com.franchiseads.variant.VariantService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

/**
 * Mockito for the loader, the Claude client and the writer; the real (pure) prompt builder, parser and validator, so
 * the tests see the actual prompts and violation messages.
 */
class GenerationServiceTests {

	private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");

	private static final Instant DEADLINE = NOW.plusSeconds(50);

	private static final AdBrief BRIEF = new AdBrief("Acme Fitness", "Plain-spoken.", List.of("cheap"),
			"Acme Downtown", "Austin", "TX", "First month free", "New year, new routine");

	private static final String VALID = """
			{"headlines": ["Acme Fitness Austin", "First Month Free", "Join Today", "Classes Every Morning",
			  "Friendly Coaches"],
			 "descriptions": ["Strength, cardio and yoga in downtown Austin.", "Your first month is on us."]}""";

	/** One violation: an exclamation mark in headlines[2]. */
	private static final String INVALID = VALID.replace("Join Today", "Join Today!");

	/** Two different violations, so tests can tell which attempt was saved. */
	private static final String INVALID_OTHER = VALID.replace("Friendly Coaches", "Cheap Friendly Coaches")
		.replace("First Month Free", "Join Today");

	private static final String MALFORMED = "Sure! Here are your ads: headlines...";

	private static final String EXCLAMATION = "EXCLAMATION_IN_HEADLINE: headlines[2] contains \"!\": \"Join Today!\"";

	private final GenerationInputLoader loader = mock(GenerationInputLoader.class);

	private final ClaudeClient claude = mock(ClaudeClient.class);

	private final VariantService variants = mock(VariantService.class);

	private final AdVariant saved = mock(AdVariant.class);

	private final ArgumentCaptor<String> userPrompts = ArgumentCaptor.forClass(String.class);

	private final ArgumentCaptor<GeneratedVariant> savedVariant = ArgumentCaptor.forClass(GeneratedVariant.class);

	private final GenerationService service = new GenerationService(loader, claude,
			new PromptBuilder(AdCopyRules.DEFAULTS), new AdCopyParser(), new AdCopyValidator(AdCopyRules.DEFAULTS),
			variants, claudeProperties(), new GenerationProperties(Duration.ofSeconds(50)), new MutableClock(NOW));

	@BeforeEach
	void setUp() {
		when(loader.load(3L, 42L)).thenReturn(new GenerationInputs(3L, 42L, BRIEF));
		when(variants.saveGenerated(any())).thenReturn(saved);
	}

	@Test
	void validFirstAttemptIsSavedAfterOneCall() {
		answers(response(VALID));

		assertThat(service.generate(3L, 42L)).isSameAs(saved);

		verifyCalls(1);
		GeneratedVariant variant = savedVariant();
		assertThat(variant.campaignId()).isEqualTo(3L);
		assertThat(variant.locationId()).isEqualTo(42L);
		assertThat(variant.headlines()).containsExactly("Acme Fitness Austin", "First Month Free", "Join Today",
				"Classes Every Morning", "Friendly Coaches");
		assertThat(variant.descriptions()).hasSize(2);
		assertThat(variant.violations()).isEmpty();
		assertThat(variant.generationAttempts()).isEqualTo(1);
		assertThat(variant.model()).isEqualTo("claude-haiku-5-5");
	}

	@Test
	void passesOneDeadlineFiftySecondsFromTheStartToEveryCall() {
		answers(response(INVALID), response(VALID));

		service.generate(3L, 42L);

		verify(claude, times(2)).complete(anyString(), anyString(), eq(DEADLINE));
	}

	@Test
	void invalidThenValidRegeneratesWithEveryViolationAndSavesTheSecondAttempt() {
		answers(response(INVALID_OTHER), response(VALID));

		service.generate(3L, 42L);

		verifyCalls(2);
		String regeneration = userPrompts.getAllValues().get(1);
		assertThat(regeneration).contains("BANNED_WORD: headlines[4] contains banned word \"cheap\": "
				+ "\"Cheap Friendly Coaches\"")
			.contains("DUPLICATE: headlines[2] repeats headlines[1]: \"Join Today\"")
			.contains("<previous_output>")
			.contains("Cheap Friendly Coaches");
		assertThat(savedVariant().violations()).isEmpty();
		assertThat(savedVariant().generationAttempts()).isEqualTo(2);
	}

	@Test
	void invalidTwiceSavesTheSecondAttemptFlaggedWithItsViolations() {
		answers(response(INVALID_OTHER), response(INVALID));

		service.generate(3L, 42L);

		verifyCalls(2);
		GeneratedVariant variant = savedVariant();
		assertThat(variant.headlines()).contains("Join Today!");
		assertThat(variant.violations()).containsExactly(EXCLAMATION);
		assertThat(variant.generationAttempts()).isEqualTo(2);
	}

	@Test
	void malformedThenValidTellsTheModelItWasNotJson() {
		answers(response(MALFORMED), response(VALID));

		service.generate(3L, 42L);

		verifyCalls(2);
		assertThat(userPrompts.getAllValues().get(1)).contains(GenerationService.NOT_JSON + "The response is not a "
				+ "single JSON object with nothing around it");
		assertThat(savedVariant().violations()).isEmpty();
		assertThat(savedVariant().generationAttempts()).isEqualTo(2);
	}

	@Test
	void malformedThenInvalidSavesTheSecondAttemptFlagged() {
		answers(response(MALFORMED), response(INVALID));

		service.generate(3L, 42L);

		assertThat(savedVariant().violations()).containsExactly(EXCLAMATION);
		assertThat(savedVariant().generationAttempts()).isEqualTo(2);
	}

	@Test
	void invalidThenMalformedSavesTheFirstAttemptFlaggedAfterTwoCalls() {
		answers(response(INVALID), response(MALFORMED));

		assertThat(service.generate(3L, 42L)).isSameAs(saved);

		verifyCalls(2);
		GeneratedVariant variant = savedVariant();
		assertThat(variant.headlines()).contains("Join Today!");
		assertThat(variant.violations()).containsExactly(EXCLAMATION);
		assertThat(variant.generationAttempts()).isEqualTo(2);
	}

	@Test
	void cutOffRegenerationCountsAsMalformed() {
		answers(response(INVALID), new ClaudeResponse(VALID, "max_tokens", "claude-haiku-5-5", 10, 10, 0, 0));

		service.generate(3L, 42L);

		assertThat(savedVariant().violations()).containsExactly(EXCLAMATION);
		assertThat(savedVariant().generationAttempts()).isEqualTo(2);
	}

	@ParameterizedTest
	@MethodSource("claudeFailures")
	void invalidThenAFailedRegenerationCallSavesTheFirstAttemptFlaggedAfterOneResponse(ClaudeException failure) {
		when(claude.complete(anyString(), anyString(), any())).thenReturn(response(INVALID)).thenThrow(failure);

		assertThat(service.generate(3L, 42L)).isSameAs(saved);

		verifyCalls(2);
		GeneratedVariant variant = savedVariant();
		assertThat(variant.headlines()).contains("Join Today!");
		assertThat(variant.violations()).containsExactly(EXCLAMATION);
		assertThat(variant.generationAttempts()).isEqualTo(1);
	}

	@Test
	void malformedTwiceIsMalformedAndSavesNothing() {
		answers(response(MALFORMED), response("{\"headlines\": []}"));

		assertThatThrownBy(() -> service.generate(3L, 42L)).isInstanceOf(MalformedAdCopyException.class)
			.hasMessage("The model returned no usable ad copy in 2 attempts; last problem: "
					+ "The JSON has no \"descriptions\" array.");

		verifyCalls(2);
		verifyNoInteractions(variants);
	}

	@ParameterizedTest
	@MethodSource("claudeFailures")
	void malformedThenAFailedRegenerationCallRethrowsAndSavesNothing(ClaudeException failure) {
		when(claude.complete(anyString(), anyString(), any())).thenReturn(response(MALFORMED)).thenThrow(failure);

		assertThatThrownBy(() -> service.generate(3L, 42L)).isSameAs(failure);

		verifyCalls(2);
		verifyNoInteractions(variants);
	}

	@ParameterizedTest
	@MethodSource("claudeFailures")
	void failedFirstCallRethrowsWithoutRegeneratingOrSaving(ClaudeException failure) {
		when(claude.complete(anyString(), anyString(), any())).thenThrow(failure);

		assertThatThrownBy(() -> service.generate(3L, 42L)).isSameAs(failure);

		verifyCalls(1);
		verifyNoInteractions(variants);
	}

	@Test
	void loaderFailureMeansNoModelCall() {
		when(loader.load(3L, 7L)).thenThrow(new LocationBrandMismatchException(7L, 3L));
		when(loader.load(3L, 8L)).thenThrow(new ResourceNotFoundException("Location", 8L));

		assertThatThrownBy(() -> service.generate(3L, 7L)).isInstanceOf(LocationBrandMismatchException.class);
		assertThatThrownBy(() -> service.generate(3L, 8L)).isInstanceOf(ResourceNotFoundException.class);

		verifyNoInteractions(claude, variants);
	}

	@Test
	void recordsTheModelThatAnsweredAndFallsBackToTheConfiguredOne() {
		answers(new ClaudeResponse(VALID, "end_turn", "claude-haiku-5-5-20261001", 10, 10, 0, 0));
		service.generate(3L, 42L);
		assertThat(savedVariant().model()).isEqualTo("claude-haiku-5-5-20261001");

		answers(new ClaudeResponse(VALID, "end_turn", null, 10, 10, 0, 0));
		service.generate(3L, 42L);
		verify(variants, times(2)).saveGenerated(savedVariant.capture());
		assertThat(savedVariant.getValue().model()).isEqualTo("configured-model");
	}

	static Stream<Named<ClaudeException>> claudeFailures() {
		return Stream.of(Named.of("timeout", new ClaudeTimeoutException("Claude deadline reached", null)),
				Named.of("unavailable", new ClaudeUnavailableException("Claude unavailable after 3 attempts", null)),
				Named.of("request rejected", new ClaudeRequestException("Claude rejected the request: HTTP 401")));
	}

	private void answers(ClaudeResponse first, ClaudeResponse... rest) {
		when(claude.complete(anyString(), anyString(), any())).thenReturn(first, rest);
	}

	/** Exactly {@code count} model calls, never more; captures the user prompts. */
	private void verifyCalls(int count) {
		verify(claude, times(count)).complete(anyString(), userPrompts.capture(), any());
		verifyNoMoreInteractions(claude);
	}

	private GeneratedVariant savedVariant() {
		verify(variants).saveGenerated(savedVariant.capture());
		return savedVariant.getValue();
	}

	private static ClaudeResponse response(String text) {
		return new ClaudeResponse(text, "end_turn", "claude-haiku-5-5", 600, 130, 0, 0);
	}

	private static ClaudeProperties claudeProperties() {
		return new ClaudeProperties("http://localhost:9", "key", "configured-model", 1024, ClaudeProperties.Effort.LOW,
				Duration.ofSeconds(1), Duration.ofSeconds(20), 3, Duration.ofMillis(500), 0.25, Duration.ofSeconds(10));
	}

}
