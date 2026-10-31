package com.franchiseads.generation;

import com.franchiseads.claude.ClaudeClient;
import com.franchiseads.claude.ClaudeException;
import com.franchiseads.claude.ClaudeProperties;
import com.franchiseads.claude.ClaudeRequestException;
import com.franchiseads.claude.ClaudeResponse;
import com.franchiseads.validation.AdCopyValidator;
import com.franchiseads.validation.Violation;
import com.franchiseads.variant.AdVariant;
import com.franchiseads.variant.GeneratedVariant;
import com.franchiseads.variant.VariantService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The generate flow from docs/flow.md: load, prompt, call, parse, validate, regenerate at most once, save.
 * <p>
 * Deliberately not {@code @Transactional} (ADR-012, ADR-029): loading and saving happen in their own transactions
 * inside {@link GenerationInputLoader} and {@link VariantService}, called through their Spring proxies, and the
 * Claude calls in between run with no transaction and no pooled connection held.
 * <p>
 * If any attempt produced parseable copy, a variant is saved from the last parseable attempt, flagged if it broke
 * any rule; only when no attempt produced parseable copy does the request fail (ADR-030).
 */
@Service
public class GenerationService {

	static final String NOT_JSON = "Your previous response was not valid JSON: ";

	private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

	private final GenerationInputLoader loader;

	private final ClaudeClient claude;

	private final PromptBuilder prompts;

	private final AdCopyParser parser;

	private final AdCopyValidator validator;

	private final VariantService variants;

	private final ClaudeProperties claudeProperties;

	private final GenerationProperties properties;

	private final Clock clock;

	GenerationService(GenerationInputLoader loader, ClaudeClient claude, PromptBuilder prompts, AdCopyParser parser,
			AdCopyValidator validator, VariantService variants, ClaudeProperties claudeProperties,
			GenerationProperties properties, Clock clock) {
		this.loader = loader;
		this.claude = claude;
		this.prompts = prompts;
		this.parser = parser;
		this.validator = validator;
		this.variants = variants;
		this.claudeProperties = claudeProperties;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * Generates, validates and saves one variant. At most 2 model calls.
	 * @throws com.franchiseads.common.error.ResourceNotFoundException unknown campaign or location
	 * @throws LocationBrandMismatchException the location belongs to another brand
	 * @throws MalformedAdCopyException no attempt produced parseable copy (both malformed)
	 * @throws ClaudeException no attempt produced parseable copy and the last call failed
	 */
	public AdVariant generate(Long campaignId, Long locationId) {
		Instant deadline = clock.instant().plus(properties.deadline());
		GenerationInputs inputs = loader.load(campaignId, locationId);
		AdBrief brief = inputs.brief();

		Attempt first = attempt(prompts.build(brief), brief, deadline);
		if (first.isValid()) {
			return save(inputs, first, 1);
		}

		Prompt regeneration = prompts.buildRegeneration(brief, first.response().text(), first.problems());
		Attempt second;
		try {
			second = attempt(regeneration, brief, deadline);
		}
		catch (ClaudeException ex) {
			if (!first.isParseable()) {
				throw ex;
			}
			logRegenerationFailure(ex);
			return save(inputs, first, 1);
		}
		if (second.isParseable()) {
			return save(inputs, second, 2);
		}
		if (first.isParseable()) {
			log.warn("Regeneration returned malformed output ({}); saving the first attempt flagged",
					second.malformed().getMessage());
			return save(inputs, first, 2);
		}
		throw new MalformedAdCopyException(
				"The model returned no usable ad copy in 2 attempts; last problem: " + second.malformed().getMessage(),
				second.malformed());
	}

	/** One model call, parsed and validated. Claude exceptions propagate; malformed output is an outcome. */
	private Attempt attempt(Prompt prompt, AdBrief brief, Instant deadline) {
		ClaudeResponse response = claude.complete(prompt.system(), prompt.user(), deadline);
		AdCopy copy;
		try {
			copy = parser.parse(response);
		}
		catch (MalformedAdCopyException ex) {
			return new Attempt(response, null, List.of(), ex);
		}
		List<String> violations = validator.validate(copy, brief.bannedWords())
			.stream()
			.map(Violation::message)
			.toList();
		return new Attempt(response, copy, violations, null);
	}

	private AdVariant save(GenerationInputs inputs, Attempt attempt, int generationAttempts) {
		String model = attempt.response().model();
		if (model == null || model.isBlank()) {
			model = claudeProperties.model();
		}
		AdVariant variant = variants.saveGenerated(new GeneratedVariant(inputs.campaignId(), inputs.locationId(),
				attempt.copy().headlines(), attempt.copy().descriptions(), attempt.violations(), generationAttempts,
				model));
		log.info("Saved variant {} for campaign {} at location {}: generation_attempts={}, validation_passed={}",
				variant.getId(), inputs.campaignId(), inputs.locationId(), generationAttempts,
				attempt.violations().isEmpty());
		return variant;
	}

	/** The failure is handled (the first attempt gets saved), so it's logged here instead of by the error handler. */
	private static void logRegenerationFailure(ClaudeException ex) {
		if (ex instanceof ClaudeRequestException) {
			log.error("Regeneration call rejected by Claude; saving the first attempt flagged", ex);
		}
		else {
			log.warn("Regeneration call failed ({}); saving the first attempt flagged", ex.getMessage());
		}
	}

	/**
	 * @param copy null when the response couldn't be parsed
	 * @param violations validator messages; empty when the copy is valid or unparseable
	 * @param malformed why parsing failed; null when it didn't
	 */
	private record Attempt(ClaudeResponse response, AdCopy copy, List<String> violations,
			MalformedAdCopyException malformed) {

		boolean isParseable() {
			return copy != null;
		}

		boolean isValid() {
			return isParseable() && violations.isEmpty();
		}

		/** What the regeneration prompt has to fix: every violation, or the parse failure. */
		List<String> problems() {
			return isParseable() ? violations : List.of(NOT_JSON + malformed.getMessage());
		}

	}

}
