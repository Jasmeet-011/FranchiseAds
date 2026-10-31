package com.franchiseads.variant;

/**
 * Approval status of an ad variant. Stored by name, never by ordinal. Transition rules arrive in M4.
 */
public enum VariantStatus {

	DRAFT, PENDING_REVIEW, APPROVED, REJECTED

}
