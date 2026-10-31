package com.franchiseads.claude;

/** The connect or read timeout expired on every attempt. Maps to 504 (docs/flow.md). */
public class ClaudeTimeoutException extends ClaudeException {

	public ClaudeTimeoutException(String message, Throwable cause) {
		super(message, cause);
	}

}
