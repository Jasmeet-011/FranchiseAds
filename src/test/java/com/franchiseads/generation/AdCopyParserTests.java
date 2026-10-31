package com.franchiseads.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.franchiseads.claude.ClaudeResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AdCopyParserTests {

	private static final String VALID = """
			{"headlines": ["Join Today", "Austin's Gym"], "descriptions": ["First month free."]}""";

	private final AdCopyParser parser = new AdCopyParser();

	private static ClaudeResponse response(String text) {
		return response(text, "end_turn");
	}

	private static ClaudeResponse response(String text, String stopReason) {
		return new ClaudeResponse(text, stopReason, "claude-haiku-5-5", 100, 50, 0, 0);
	}

	@Test
	void parsesAPlainJsonObject() {
		AdCopy copy = parser.parse(response(VALID));

		assertThat(copy.headlines()).containsExactly("Join Today", "Austin's Gym");
		assertThat(copy.descriptions()).containsExactly("First month free.");
	}

	@Test
	void stripsAJsonMarkdownFence() {
		AdCopy copy = parser.parse(response("```json\n" + VALID + "\n```"));

		assertThat(copy.headlines()).containsExactly("Join Today", "Austin's Gym");
	}

	@Test
	void stripsABareMarkdownFence() {
		AdCopy copy = parser.parse(response("```\n" + VALID + "\n```"));

		assertThat(copy.descriptions()).containsExactly("First month free.");
	}

	@Test
	void toleratesWhitespaceAroundTheFenceAndTheJson() {
		AdCopy copy = parser.parse(response("\n  ```json\n\n" + VALID + "  \n```\n\n"));

		assertThat(copy.headlines()).hasSize(2);
	}

	@Test
	void trimsEveryEntry() {
		AdCopy copy = parser.parse(response("""
				{"headlines": ["  Join Today ", "\\tAustin's Gym\\n"], "descriptions": [" First month free.  "]}"""));

		assertThat(copy.headlines()).containsExactly("Join Today", "Austin's Gym");
		assertThat(copy.descriptions()).containsExactly("First month free.");
	}

	@Test
	void ignoresExtraKeys() {
		AdCopy copy = parser.parse(response("""
				{"headlines": ["Join Today"], "descriptions": ["Free month."], "notes": "fits", "paths": ["gym"]}"""));

		assertThat(copy).isEqualTo(new AdCopy(List.of("Join Today"), List.of("Free month.")));
	}

	@Test
	void leavesEmptyArraysAndBlankEntriesToTheValidator() {
		AdCopy copy = parser.parse(response("{\"headlines\": [], \"descriptions\": [\"   \"]}"));

		assertThat(copy.headlines()).isEmpty();
		assertThat(copy.descriptions()).containsExactly("");
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', textBlock = """
			prose before the JSON      | Here you go: {"headlines": ["a"], "descriptions": ["b"]}  | not a single JSON object
			prose after the JSON       | {"headlines": ["a"], "descriptions": ["b"]} Hope this helps! | not a single JSON object
			prose around a fence       | Sure! ```json {"headlines": ["a"], "descriptions": ["b"]} ``` | not a single JSON object
			two JSON objects           | {"headlines": ["a"], "descriptions": ["b"]} {}            | not a single JSON object
			not JSON at all            | I cannot help with that.                                | not a single JSON object
			truncated JSON             | {"headlines": ["a", "b"                                    | not a single JSON object
			JSON array                 | [{"headlines": ["a"], "descriptions": ["b"]}]             | JSON but not an object
			missing headlines          | {"descriptions": ["b"]}                                   | no "headlines" array
			missing descriptions       | {"headlines": ["a"]}                                      | no "descriptions" array
			null headlines             | {"headlines": null, "descriptions": ["b"]}                | no "headlines" array
			headlines as a string      | {"headlines": "a", "descriptions": ["b"]}                 | "headlines" is not an array
			descriptions as an object  | {"headlines": ["a"], "descriptions": {"0": "b"}}          | "descriptions" is not an array
			number item                | {"headlines": ["a", 2], "descriptions": ["b"]}            | headlines[1] is not a string
			null item                  | {"headlines": ["a"], "descriptions": [null]}              | descriptions[0] is not a string
			nested array item          | {"headlines": [["a"]], "descriptions": ["b"]}             | headlines[0] is not a string
			""")
	void rejectsAnythingButTheExpectedObject(String scenario, String text, String expectedMessage) {
		assertThatThrownBy(() -> parser.parse(response(text))).isInstanceOf(MalformedAdCopyException.class)
			.hasMessageContaining(expectedMessage);
	}

	@Test
	void rejectsAResponseWithNoText() {
		assertThatThrownBy(() -> parser.parse(response("  "))).isInstanceOf(MalformedAdCopyException.class)
			.hasMessage("The response contained no text.");
	}

	@Test
	void treatsMaxTokensAsTruncatedEvenIfTheTextParses() {
		assertThatThrownBy(() -> parser.parse(response(VALID, "max_tokens")))
			.isInstanceOf(MalformedAdCopyException.class)
			.hasMessage("The response was cut off at max_tokens, so its JSON is probably incomplete.");
	}

	@Test
	void treatsARefusalAsMalformed() {
		assertThatThrownBy(() -> parser.parse(response("", "refusal"))).isInstanceOf(MalformedAdCopyException.class)
			.hasMessage("The model refused to answer (stop_reason refusal).");
	}

	@Test
	void treatsAnyOtherStopReasonAsMalformed() {
		assertThatThrownBy(() -> parser.parse(response(VALID, "pause_turn")))
			.isInstanceOf(MalformedAdCopyException.class)
			.hasMessage("The response stopped with stop_reason pause_turn instead of end_turn.");
		assertThatThrownBy(() -> parser.parse(response(VALID, null))).isInstanceOf(MalformedAdCopyException.class)
			.hasMessage("The response stopped with stop_reason null instead of end_turn.");
	}

}
