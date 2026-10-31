package com.franchiseads.validation;

import com.franchiseads.generation.AdCopy;
import com.franchiseads.validation.Violation.Rule;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Decides whether ad copy is usable (ADR-010). Pure and deterministic: no Spring, no I/O. Counts and length limits
 * come from {@link AdCopyRules} (ADR-024); lengths are Unicode code points (ADR-013).
 * <p>
 * Violations come in a fixed order: headlines, then descriptions; within each list the count first, then each entry
 * by index, and for each entry the rules in {@link Rule} order. A blank entry is reported as {@code BLANK} only.
 * Every other entry is checked against every rule, even when the count is wrong, so the model gets complete feedback.
 */
public class AdCopyValidator {

	private static final String HEADLINES = "headlines";

	private static final String DESCRIPTIONS = "descriptions";

	private final AdCopyRules rules;

	public AdCopyValidator(AdCopyRules rules) {
		this.rules = rules;
	}

	/**
	 * @param bannedWords the brand's banned words and phrases; blank entries are ignored
	 * @return every violation, in the order described above; empty when the copy is valid
	 */
	public List<Violation> validate(AdCopy copy, List<String> bannedWords) {
		List<BannedPhrase> banned = bannedWords.stream()
			.filter(word -> !word.isBlank())
			.map(BannedPhrase::of)
			.toList();
		List<Violation> violations = new ArrayList<>();
		check(HEADLINES, copy.headlines(), rules.headlineCount(), rules.headlineMaxLength(), Rule.HEADLINE_TOO_LONG,
				banned, violations);
		check(DESCRIPTIONS, copy.descriptions(), rules.descriptionCount(), rules.descriptionMaxLength(),
				Rule.DESCRIPTION_TOO_LONG, banned, violations);
		return List.copyOf(violations);
	}

	private static void check(String field, List<String> entries, int expectedCount, int maxLength, Rule tooLong,
			List<BannedPhrase> banned, List<Violation> violations) {
		if (entries.size() != expectedCount) {
			violations.add(new Violation(Rule.WRONG_COUNT, field, null, null, "%s: %s has %d entries (expected %d)"
				.formatted(Rule.WRONG_COUNT, field, entries.size(), expectedCount)));
		}
		Map<String, Integer> firstIndexByKey = new HashMap<>();
		for (int i = 0; i < entries.size(); i++) {
			String value = entries.get(i);
			String position = field + "[" + i + "]";
			if (value.isBlank()) {
				violations.add(violation(Rule.BLANK, field, i, value, position + " is blank"));
				continue;
			}
			int length = value.codePointCount(0, value.length());
			if (length > maxLength) {
				violations.add(violation(tooLong, field, i, value,
						"%s is %d characters (max %d): \"%s\"".formatted(position, length, maxLength, value)));
			}
			if (HEADLINES.equals(field) && value.contains("!")) {
				violations.add(violation(Rule.EXCLAMATION_IN_HEADLINE, field, i, value,
						"%s contains \"!\": \"%s\"".formatted(position, value)));
			}
			for (BannedPhrase phrase : banned) {
				if (phrase.pattern().matcher(value).find()) {
					violations.add(violation(Rule.BANNED_WORD, field, i, value,
							"%s contains banned word \"%s\": \"%s\"".formatted(position, phrase.word(), value)));
				}
			}
			Integer first = firstIndexByKey.putIfAbsent(value.strip().toLowerCase(Locale.ROOT), i);
			if (first != null) {
				violations.add(violation(Rule.DUPLICATE, field, i, value,
						"%s repeats %s[%d]: \"%s\"".formatted(position, field, first, value)));
			}
		}
	}

	private static Violation violation(Rule rule, String field, int index, String value, String detail) {
		return new Violation(rule, field, index, value, rule + ": " + detail);
	}

	/**
	 * A banned word or phrase as a whole-word, case-insensitive pattern (ADR-028). Whitespace inside a phrase matches
	 * any run of whitespace, and the phrase must not touch a letter or digit on either side, so "cheap" doesn't match
	 * "cheapest" but does match "cheap-looking".
	 */
	private record BannedPhrase(String word, Pattern pattern) {

		static BannedPhrase of(String word) {
			String body = Arrays.stream(word.strip().split("\\s+"))
				.map(Pattern::quote)
				.collect(Collectors.joining("\\s+"));
			Pattern pattern = Pattern.compile("(?<![\\p{L}\\p{N}])" + body + "(?![\\p{L}\\p{N}])",
					Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
			return new BannedPhrase(word.strip(), pattern);
		}

	}

}
