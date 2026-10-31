package com.franchiseads.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.franchiseads.generation.AdCopy;
import com.franchiseads.validation.Violation.Rule;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Plain JUnit: the validator is pure. Default rules are 5 headlines (max 30) and 2 descriptions (max 90). */
class AdCopyValidatorTests {

	private static final List<String> HEADLINES = List.of("Acme Fitness Austin", "First Month Free", "Join Today",
			"Classes Every Morning", "Friendly Coaches");

	private static final List<String> DESCRIPTIONS = List.of("Strength, cardio and yoga classes in downtown Austin.",
			"Sign up this week and your first month is on us.");

	private final AdCopyValidator validator = new AdCopyValidator(AdCopyRules.DEFAULTS);

	@Test
	void acceptsValidCopy() {
		assertThat(validator.validate(new AdCopy(HEADLINES, DESCRIPTIONS), List.of("cheap", "best in town")))
			.isEmpty();
	}

	// --- WRONG_COUNT ---

	@Test
	void rejectsTooFewHeadlines() {
		assertThat(messages(HEADLINES.subList(0, 4), DESCRIPTIONS))
			.containsExactly("WRONG_COUNT: headlines has 4 entries (expected 5)");
	}

	@Test
	void rejectsTooManyHeadlines() {
		assertThat(messages(with(HEADLINES, "Open Late"), DESCRIPTIONS))
			.containsExactly("WRONG_COUNT: headlines has 6 entries (expected 5)");
	}

	@Test
	void rejectsTooFewAndTooManyDescriptions() {
		assertThat(messages(HEADLINES, DESCRIPTIONS.subList(0, 1)))
			.containsExactly("WRONG_COUNT: descriptions has 1 entries (expected 2)");
		assertThat(messages(HEADLINES, with(DESCRIPTIONS, "Free parking.")))
			.containsExactly("WRONG_COUNT: descriptions has 3 entries (expected 2)");
	}

	@Test
	void rejectsEmptyLists() {
		assertThat(messages(List.of(), List.of())).containsExactly(
				"WRONG_COUNT: headlines has 0 entries (expected 5)",
				"WRONG_COUNT: descriptions has 0 entries (expected 2)");
	}

	@Test
	void stillChecksEveryEntryWhenTheCountIsWrong() {
		assertThat(messages(List.of("Join Now!", "Join now!"), DESCRIPTIONS)).containsExactly(
				"WRONG_COUNT: headlines has 2 entries (expected 5)",
				"EXCLAMATION_IN_HEADLINE: headlines[0] contains \"!\": \"Join Now!\"",
				"EXCLAMATION_IN_HEADLINE: headlines[1] contains \"!\": \"Join now!\"",
				"DUPLICATE: headlines[1] repeats headlines[0]: \"Join now!\"");
	}

	// --- length, in code points ---

	@Test
	void acceptsHeadlineOfExactly30Characters() {
		assertThat(messages(replace(HEADLINES, 2, "a".repeat(30)), DESCRIPTIONS)).isEmpty();
	}

	@Test
	void rejectsHeadlineLongerThan30Characters() {
		String headline = "a".repeat(31);

		assertThat(messages(replace(HEADLINES, 2, headline), DESCRIPTIONS))
			.containsExactly("HEADLINE_TOO_LONG: headlines[2] is 31 characters (max 30): \"" + headline + "\"");
	}

	@Test
	void acceptsDescriptionOfExactly90Characters() {
		assertThat(messages(HEADLINES, replace(DESCRIPTIONS, 1, "d".repeat(90)))).isEmpty();
	}

	@Test
	void rejectsDescriptionLongerThan90Characters() {
		String description = "d".repeat(91);

		assertThat(messages(HEADLINES, replace(DESCRIPTIONS, 1, description)))
			.containsExactly("DESCRIPTION_TOO_LONG: descriptions[1] is 91 characters (max 90): \"" + description + "\"");
	}

	@Test
	void countsAnEmojiAsOneCharacter() {
		String headline = "a".repeat(29) + "💪"; // 💪, one code point, two UTF-16 units
		assertThat(headline.length()).isEqualTo(31);

		assertThat(messages(replace(HEADLINES, 0, headline), DESCRIPTIONS)).isEmpty();
	}

	@Test
	void reportsEmojiHeadlineLengthInCodePoints() {
		String headline = "a".repeat(30) + "💪";

		assertThat(messages(replace(HEADLINES, 0, headline), DESCRIPTIONS))
			.containsExactly("HEADLINE_TOO_LONG: headlines[0] is 31 characters (max 30): \"" + headline + "\"");
	}

	// --- BLANK ---

	@ParameterizedTest
	@ValueSource(strings = { "", "   ", "\t" })
	void rejectsBlankHeadlinesAndDescriptions(String blank) {
		assertThat(messages(replace(HEADLINES, 1, blank), replace(DESCRIPTIONS, 0, blank)))
			.containsExactly("BLANK: headlines[1] is blank", "BLANK: descriptions[0] is blank");
	}

	@Test
	void reportsTwoBlanksAsBlankNotAsDuplicates() {
		assertThat(messages(replace(replace(HEADLINES, 1, ""), 3, " "), DESCRIPTIONS))
			.containsExactly("BLANK: headlines[1] is blank", "BLANK: headlines[3] is blank");
	}

	// --- EXCLAMATION_IN_HEADLINE ---

	@Test
	void rejectsExclamationMarkInHeadline() {
		assertThat(messages(replace(HEADLINES, 4, "Join Today!"), DESCRIPTIONS))
			.containsExactly("EXCLAMATION_IN_HEADLINE: headlines[4] contains \"!\": \"Join Today!\"");
	}

	@Test
	void allowsExclamationMarkInDescription() {
		assertThat(messages(HEADLINES, replace(DESCRIPTIONS, 0, "Your first month is free!"))).isEmpty();
	}

	// --- BANNED_WORD ---

	@Test
	void rejectsBannedWordIgnoringCase() {
		assertThat(messages(replace(HEADLINES, 3, "CHEAP Classes Downtown"), DESCRIPTIONS, "cheap"))
			.containsExactly("BANNED_WORD: headlines[3] contains banned word \"cheap\": \"CHEAP Classes Downtown\"");
	}

	@Test
	void doesNotMatchBannedWordInsideALongerWord() {
		assertThat(messages(replace(HEADLINES, 3, "Cheapest Gym In Austin"), DESCRIPTIONS, "cheap")).isEmpty();
		assertThat(messages(replace(HEADLINES, 3, "Not Overcheap"), DESCRIPTIONS, "cheap")).isEmpty();
	}

	@Test
	void treatsPunctuationAndHyphensAsWordBoundaries() {
		assertThat(messages(replace(replace(HEADLINES, 0, "Cheap-Looking? No."), 1, "Fast, cheap."), DESCRIPTIONS,
				"cheap"))
			.containsExactly("BANNED_WORD: headlines[0] contains banned word \"cheap\": \"Cheap-Looking? No.\"",
					"BANNED_WORD: headlines[1] contains banned word \"cheap\": \"Fast, cheap.\"");
	}

	@Test
	void matchesBannedPhraseAcrossSpaces() {
		assertThat(messages(replace(replace(HEADLINES, 0, "Best In Town Classes"), 1, "The best  in town"),
				DESCRIPTIONS, "best in town"))
			.containsExactly(
					"BANNED_WORD: headlines[0] contains banned word \"best in town\": \"Best In Town Classes\"",
					"BANNED_WORD: headlines[1] contains banned word \"best in town\": \"The best  in town\"");
	}

	@Test
	void doesNotMatchBannedPhraseWithDifferentWords() {
		assertThat(messages(replace(replace(HEADLINES, 0, "Best Inn Town"), 1, "Bestintown Gym"), DESCRIPTIONS,
				"best in town"))
			.isEmpty();
	}

	@Test
	void rejectsBannedWordInDescription() {
		String description = "The best in town, at a cheap price.";

		assertThat(messages(HEADLINES, replace(DESCRIPTIONS, 1, description), "cheap", "best in town")).containsExactly(
				"BANNED_WORD: descriptions[1] contains banned word \"cheap\": \"" + description + "\"",
				"BANNED_WORD: descriptions[1] contains banned word \"best in town\": \"" + description + "\"");
	}

	@Test
	void reportsEachBannedWordOncePerEntry() {
		assertThat(messages(replace(HEADLINES, 2, "Cheap, Cheap, Cheap"), DESCRIPTIONS, "cheap"))
			.containsExactly("BANNED_WORD: headlines[2] contains banned word \"cheap\": \"Cheap, Cheap, Cheap\"");
	}

	@Test
	void treatsRegexCharactersInBannedWordsLiterally() {
		assertThat(messages(replace(HEADLINES, 2, "Get 50% Off Today"), DESCRIPTIONS, "50% off"))
			.containsExactly("BANNED_WORD: headlines[2] contains banned word \"50% off\": \"Get 50% Off Today\"");
		assertThat(messages(replace(HEADLINES, 2, "axb Training"), DESCRIPTIONS, "a.b")).isEmpty();
	}

	@Test
	void matchesBannedWordWithNonAsciiLettersIgnoringCase() {
		assertThat(messages(replace(HEADLINES, 2, "CAFÉ After Class"), DESCRIPTIONS, "café"))
			.containsExactly("BANNED_WORD: headlines[2] contains banned word \"café\": \"CAFÉ After Class\"");
	}

	@Test
	void ignoresBlankBannedWords() {
		assertThat(messages(HEADLINES, DESCRIPTIONS, " ", "")).isEmpty();
	}

	// --- DUPLICATE ---

	@Test
	void rejectsHeadlinesThatDifferOnlyInCase() {
		assertThat(messages(replace(HEADLINES, 3, "join today"), DESCRIPTIONS))
			.containsExactly("DUPLICATE: headlines[3] repeats headlines[2]: \"join today\"");
	}

	@Test
	void rejectsHeadlinesThatDifferOnlyInSurroundingSpaces() {
		assertThat(messages(replace(replace(HEADLINES, 0, " Join today"), 2, "Join Today  "), DESCRIPTIONS))
			.containsExactly("DUPLICATE: headlines[2] repeats headlines[0]: \"Join Today  \"");
	}

	@Test
	void reportsEveryRepeatAgainstTheFirstOccurrence() {
		assertThat(messages(List.of("Join Today", "Open Late", "JOIN TODAY", "Free Parking", "join today"),
				DESCRIPTIONS))
			.containsExactly("DUPLICATE: headlines[2] repeats headlines[0]: \"JOIN TODAY\"",
					"DUPLICATE: headlines[4] repeats headlines[0]: \"join today\"");
	}

	@Test
	void rejectsDuplicateDescriptions() {
		assertThat(messages(HEADLINES, List.of("Sign up today.", "sign up today.")))
			.containsExactly("DUPLICATE: descriptions[1] repeats descriptions[0]: \"sign up today.\"");
	}

	@Test
	void allowsTheSameTextInAHeadlineAndADescription() {
		assertThat(messages(replace(HEADLINES, 0, "Join today."), replace(DESCRIPTIONS, 0, "Join today.")))
			.isEmpty();
	}

	// --- ordering, configuration, structure ---

	@Test
	void listsViolationsByFieldThenIndexThenRule() {
		List<String> headlines = List.of("Cheap Gym Deals Everywhere Now!", "", "Open Late", "open late");
		List<String> descriptions = List.of("d".repeat(91));

		assertThat(messages(headlines, descriptions, "cheap")).containsExactly(
				"WRONG_COUNT: headlines has 4 entries (expected 5)",
				"HEADLINE_TOO_LONG: headlines[0] is 31 characters (max 30): \"Cheap Gym Deals Everywhere Now!\"",
				"EXCLAMATION_IN_HEADLINE: headlines[0] contains \"!\": \"Cheap Gym Deals Everywhere Now!\"",
				"BANNED_WORD: headlines[0] contains banned word \"cheap\": \"Cheap Gym Deals Everywhere Now!\"",
				"BLANK: headlines[1] is blank",
				"DUPLICATE: headlines[3] repeats headlines[2]: \"open late\"",
				"WRONG_COUNT: descriptions has 1 entries (expected 2)",
				"DESCRIPTION_TOO_LONG: descriptions[0] is 91 characters (max 90): \"" + "d".repeat(91) + "\"");
	}

	@Test
	void usesCountsAndLimitsFromTheRules() {
		AdCopyValidator custom = new AdCopyValidator(new AdCopyRules(3, 3, 25, 60));
		List<String> headlines = List.of("Join Today", "a".repeat(26), "Open Late");
		List<String> descriptions = List.of("One.", "Two.", "e".repeat(61));

		assertThat(custom.validate(new AdCopy(headlines, descriptions), List.of())).extracting(Violation::message)
			.containsExactly("HEADLINE_TOO_LONG: headlines[1] is 26 characters (max 25): \"" + "a".repeat(26) + "\"",
					"DESCRIPTION_TOO_LONG: descriptions[2] is 61 characters (max 60): \"" + "e".repeat(61) + "\"");
		assertThat(custom.validate(new AdCopy(HEADLINES, DESCRIPTIONS), List.of())).extracting(Violation::message)
			.containsExactly("WRONG_COUNT: headlines has 5 entries (expected 3)",
					"WRONG_COUNT: descriptions has 2 entries (expected 3)");
	}

	@Test
	void violationNamesRuleFieldIndexAndValue() {
		List<Violation> violations = validator.validate(new AdCopy(replace(HEADLINES, 4, "Join Today!"), List.of()),
				List.of());

		assertThat(violations).containsExactly(
				new Violation(Rule.EXCLAMATION_IN_HEADLINE, "headlines", 4, "Join Today!",
						"EXCLAMATION_IN_HEADLINE: headlines[4] contains \"!\": \"Join Today!\""),
				new Violation(Rule.WRONG_COUNT, "descriptions", null, null,
						"WRONG_COUNT: descriptions has 0 entries (expected 2)"));
	}

	private List<String> messages(List<String> headlines, List<String> descriptions, String... bannedWords) {
		return validator.validate(new AdCopy(headlines, descriptions), List.of(bannedWords))
			.stream()
			.map(Violation::message)
			.toList();
	}

	private static List<String> replace(List<String> list, int index, String value) {
		List<String> copy = new ArrayList<>(list);
		copy.set(index, value);
		return copy;
	}

	private static List<String> with(List<String> list, String extra) {
		List<String> copy = new ArrayList<>(list);
		copy.add(extra);
		return copy;
	}

}
