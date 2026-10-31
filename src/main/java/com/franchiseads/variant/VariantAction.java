package com.franchiseads.variant;

import java.util.Locale;

/** A review action on a variant. Which status each one is allowed from is decided by {@link VariantStatus#next}. */
public enum VariantAction {

	SUBMIT, APPROVE, REJECT;

	/** The action as it appears in the endpoint path and in messages: {@code submit}, {@code approve}, {@code reject}. */
	public String verb() {
		return name().toLowerCase(Locale.ROOT);
	}

}
