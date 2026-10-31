package com.franchiseads.variant;

/**
 * A review action that isn't allowed from the variant's current status, or a submit of a variant that failed
 * validation. Thrown only by {@link VariantStatus#next}; the message is meant for the API client.
 */
public class IllegalTransitionException extends RuntimeException {

	private final VariantStatus currentStatus;

	private final VariantAction attemptedAction;

	IllegalTransitionException(VariantStatus currentStatus, VariantAction attemptedAction, String message) {
		super(message);
		this.currentStatus = currentStatus;
		this.attemptedAction = attemptedAction;
	}

	public VariantStatus getCurrentStatus() {
		return currentStatus;
	}

	public VariantAction getAttemptedAction() {
		return attemptedAction;
	}

}
