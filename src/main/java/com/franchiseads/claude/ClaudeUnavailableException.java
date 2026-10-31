package com.franchiseads.claude;

/**
 * Claude kept answering 429 or 5xx, couldn't be reached, or returned a body that isn't a Messages response, on every
 * attempt. Maps to 503 (docs/flow.md).
 */
public class ClaudeUnavailableException extends ClaudeException {

	public ClaudeUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

}
