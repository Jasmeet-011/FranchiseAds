package com.franchiseads.claude;

/**
 * Claude rejected the request with a non-retryable 4xx (bad request, bad key, ...), or no API key is configured.
 * Never retried. Maps to 502 (docs/flow.md).
 */
public class ClaudeRequestException extends ClaudeException {

	public ClaudeRequestException(String message) {
		super(message, null);
	}

}
