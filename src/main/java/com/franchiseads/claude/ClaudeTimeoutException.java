package com.franchiseads.claude;

/**
 * The last attempt hit its connect or read timeout, or the request deadline left no time for another attempt or
 * retry delay. Maps to 504 (docs/flow.md).
 */
public class ClaudeTimeoutException extends ClaudeException {

	public ClaudeTimeoutException(String message, Throwable cause) {
		super(message, cause);
	}

}
