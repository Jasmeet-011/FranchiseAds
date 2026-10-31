package com.franchiseads.claude;

import java.time.Duration;

/** Properties and API bodies shaped like the documented Messages API responses. */
final class ClaudeFixtures {

	static final String API_KEY = "test-key-not-real";

	static final String MODEL = "claude-haiku-5-5";

	private ClaudeFixtures() {
	}

	static ClaudeProperties properties(String baseUrl) {
		return properties(baseUrl, API_KEY);
	}

	static ClaudeProperties properties(String baseUrl, String apiKey) {
		return properties(baseUrl, apiKey, Duration.ofSeconds(2), 3);
	}

	static ClaudeProperties properties(String baseUrl, String apiKey, Duration readTimeout, int maxAttempts) {
		return new ClaudeProperties(baseUrl, apiKey, MODEL, 1024, Duration.ofSeconds(1), readTimeout, maxAttempts,
				Duration.ofMillis(500), 0.25, Duration.ofSeconds(10));
	}

	/** A successful response with one text block. {@code text} must already be JSON-escaped. */
	static String message(String text) {
		return message("end_turn", "{\"type\":\"text\",\"text\":\"" + text + "\"}");
	}

	static String message(String stopReason, String... contentBlocks) {
		return """
				{"id":"msg_01","type":"message","role":"assistant","model":"%s",
				 "content":[%s],
				 "stop_reason":"%s","stop_sequence":null,
				 "usage":{"input_tokens":120,"output_tokens":45}}
				""".formatted(MODEL, String.join(",", contentBlocks), stopReason);
	}

	static String error(String type, String message) {
		return """
				{"type":"error","error":{"type":"%s","message":"%s"},"request_id":"req_body_01"}
				""".formatted(type, message);
	}

}
