package com.franchiseads.claude;

/**
 * One successful Messages API call. The text is untrusted model output: it still has to go through the parser and
 * the validator.
 *
 * @param text every {@code text} content block, concatenated in order; other block types are left out
 * @param stopReason e.g. {@code end_turn}, {@code max_tokens}, {@code refusal}; may be null
 * @param model the model that answered
 * @param inputTokens {@code usage.input_tokens}
 * @param outputTokens {@code usage.output_tokens}, thinking included
 * @param thinkingBlocks how many {@code thinking} or {@code redacted_thinking} blocks came back
 * @param thinkingTokens {@code usage.output_tokens_details.thinking_tokens}, the part of {@code outputTokens} spent
 * thinking; 0 when the API doesn't report it
 */
public record ClaudeResponse(String text, String stopReason, String model, int inputTokens, int outputTokens,
		int thinkingBlocks, int thinkingTokens) {
}
