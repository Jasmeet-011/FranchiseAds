package com.franchiseads.generation;

/**
 * The model's response can't be read as ad copy: not the expected JSON, or cut off. Triggers the one regeneration;
 * malformed twice maps to 502 (docs/flow.md).
 */
public class MalformedAdCopyException extends RuntimeException {

	public MalformedAdCopyException(String message) {
		super(message);
	}

	public MalformedAdCopyException(String message, Throwable cause) {
		super(message, cause);
	}

}
