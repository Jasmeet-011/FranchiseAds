package com.franchiseads.validation;

/**
 * One broken rule in a set of ad copy. {@link #message()} is what gets stored, returned to clients and fed back to the
 * model, e.g. {@code HEADLINE_TOO_LONG: headlines[2] is 34 characters (max 30): "..."} (docs/flow.md).
 *
 * @param rule which rule
 * @param field {@code headlines} or {@code descriptions}
 * @param index position in the list; null for {@link Rule#WRONG_COUNT}
 * @param value the offending entry; null for {@link Rule#WRONG_COUNT}
 * @param message the full human-readable violation
 */
public record Violation(Rule rule, String field, Integer index, String value, String message) {

	/** The rules in docs/flow.md, in the order they're checked for each entry. */
	public enum Rule {

		WRONG_COUNT, BLANK, HEADLINE_TOO_LONG, DESCRIPTION_TOO_LONG, EXCLAMATION_IN_HEADLINE, BANNED_WORD, DUPLICATE

	}

}
