package com.franchiseads.generation;

import com.franchiseads.claude.ClaudeResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Model response → {@link AdCopy} (docs/flow.md, step 4). Tolerates exactly two things: one markdown code fence around
 * the JSON, and whitespace around entries. Everything else that isn't a single
 * {@code {"headlines": [...], "descriptions": [...]}} object is {@link MalformedAdCopyException}. Extra keys are
 * ignored. Counts, blank entries and lengths are left to the validator.
 */
public class AdCopyParser {

	/** A whole response wrapped in ```json ... ``` (any or no language tag). */
	private static final Pattern FENCED = Pattern.compile("\\A```[A-Za-z0-9_-]*\\s*(.*?)\\s*```\\z", Pattern.DOTALL);

	private final JsonMapper json = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

	/**
	 * @throws MalformedAdCopyException if the response was cut off or refused, or its text isn't the expected JSON
	 */
	public AdCopy parse(ClaudeResponse response) {
		String stopReason = response.stopReason();
		if ("max_tokens".equals(stopReason)) {
			throw new MalformedAdCopyException(
					"The response was cut off at max_tokens, so its JSON is probably incomplete.");
		}
		if ("refusal".equals(stopReason)) {
			throw new MalformedAdCopyException("The model refused to answer (stop_reason refusal).");
		}
		if (!"end_turn".equals(stopReason)) {
			throw new MalformedAdCopyException(
					"The response stopped with stop_reason " + stopReason + " instead of end_turn.");
		}
		return parseText(response.text());
	}

	private AdCopy parseText(String text) {
		if (text == null || text.isBlank()) {
			throw new MalformedAdCopyException("The response contained no text.");
		}
		String body = text.strip();
		Matcher fenced = FENCED.matcher(body);
		if (fenced.matches()) {
			body = fenced.group(1);
		}
		JsonNode root;
		try {
			root = json.readTree(body);
		}
		catch (JacksonException ex) {
			throw new MalformedAdCopyException(
					"The response is not a single JSON object with nothing around it: " + ex.getOriginalMessage(), ex);
		}
		if (!root.isObject()) {
			throw new MalformedAdCopyException("The response is JSON but not an object.");
		}
		return new AdCopy(strings(root, "headlines"), strings(root, "descriptions"));
	}

	private static List<String> strings(JsonNode root, String key) {
		JsonNode node = root.get(key);
		if (node == null || node.isNull()) {
			throw new MalformedAdCopyException("The JSON has no \"" + key + "\" array.");
		}
		if (!node.isArray()) {
			throw new MalformedAdCopyException("\"" + key + "\" is not an array.");
		}
		List<String> values = new ArrayList<>(node.size());
		for (int i = 0; i < node.size(); i++) {
			JsonNode item = node.get(i);
			if (!item.isString()) {
				throw new MalformedAdCopyException(key + "[" + i + "] is not a string.");
			}
			values.add(item.stringValue().strip());
		}
		return values;
	}

}
