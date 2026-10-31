package com.franchiseads.claude;

/** A Claude call that produced no usable response. Messages never contain the API key or the prompt. */
public abstract class ClaudeException extends RuntimeException {

	protected ClaudeException(String message, Throwable cause) {
		super(message, cause);
	}

}
