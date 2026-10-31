package com.franchiseads.common;

/**
 * Input normalization for request records (ADR-017). Required fields are stripped so whitespace-only values fail
 * {@code @NotBlank}; optional fields that are blank become {@code null}.
 */
public final class Strings {

	private Strings() {
	}

	public static String strip(String value) {
		return (value != null) ? value.strip() : null;
	}

	public static String stripToNull(String value) {
		String stripped = strip(value);
		return (stripped == null || stripped.isEmpty()) ? null : stripped;
	}

}
