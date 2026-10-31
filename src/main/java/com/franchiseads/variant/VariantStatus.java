package com.franchiseads.variant;

import java.util.Arrays;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Approval status of an ad variant. Stored by name, never by ordinal. {@link #next} holds every transition rule
 * (ADR-032): nothing else decides whether an action is allowed.
 */
public enum VariantStatus {

	DRAFT, PENDING_REVIEW, APPROVED, REJECTED;

	/**
	 * The status a variant in this status moves to under {@code action}.
	 * @param validationPassed whether the variant passed validation; a flagged variant can't be submitted
	 * @throws IllegalTransitionException if the action isn't allowed from this status, or it's a submit of a flagged
	 * variant. The status is checked first.
	 */
	public VariantStatus next(VariantAction action, boolean validationPassed) {
		VariantStatus target = target(action);
		if (target == null) {
			throw new IllegalTransitionException(this, action, notAllowedMessage(action));
		}
		if (action == VariantAction.SUBMIT && !validationPassed) {
			throw new IllegalTransitionException(this, action,
					"Can't submit a variant that failed validation: flagged variants can't enter review. "
							+ "Generate a new variant for this location instead.");
		}
		return target;
	}

	/** The transition table: the target for {@code action}, or null if it isn't allowed from this status. */
	private VariantStatus target(VariantAction action) {
		return switch (this) {
			case DRAFT -> (action == VariantAction.SUBMIT) ? PENDING_REVIEW : null;
			case PENDING_REVIEW -> switch (action) {
				case APPROVE -> APPROVED;
				case REJECT -> REJECTED;
				case SUBMIT -> null;
			};
			case APPROVED, REJECTED -> null;
		};
	}

	private boolean isFinal() {
		return Arrays.stream(VariantAction.values()).allMatch(action -> target(action) == null);
	}

	private String notAllowedMessage(VariantAction action) {
		String prefix = "Can't " + action.verb() + " a variant in status " + this + ": ";
		if (isFinal()) {
			return prefix + names(VariantStatus::isFinal)
					+ " are final. Generate a new variant for this location instead.";
		}
		return prefix + action.verb() + " is allowed only from " + names(status -> status.target(action) != null)
				+ ".";
	}

	private static String names(Predicate<VariantStatus> filter) {
		return Arrays.stream(values()).filter(filter).map(VariantStatus::name).collect(Collectors.joining(" and "));
	}

}
