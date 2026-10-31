package com.franchiseads.variant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VariantStatusTests {

	/** Every status × action × validationPassed combination; "rejected" means 409. */
	@ParameterizedTest(name = "{0} + {1} (validationPassed={2}) -> {3}")
	@CsvSource(textBlock = """
			DRAFT,          SUBMIT,  true,  PENDING_REVIEW
			DRAFT,          SUBMIT,  false, rejected
			DRAFT,          APPROVE, true,  rejected
			DRAFT,          APPROVE, false, rejected
			DRAFT,          REJECT,  true,  rejected
			DRAFT,          REJECT,  false, rejected
			PENDING_REVIEW, SUBMIT,  true,  rejected
			PENDING_REVIEW, SUBMIT,  false, rejected
			PENDING_REVIEW, APPROVE, true,  APPROVED
			PENDING_REVIEW, APPROVE, false, APPROVED
			PENDING_REVIEW, REJECT,  true,  REJECTED
			PENDING_REVIEW, REJECT,  false, REJECTED
			APPROVED,       SUBMIT,  true,  rejected
			APPROVED,       SUBMIT,  false, rejected
			APPROVED,       APPROVE, true,  rejected
			APPROVED,       APPROVE, false, rejected
			APPROVED,       REJECT,  true,  rejected
			APPROVED,       REJECT,  false, rejected
			REJECTED,       SUBMIT,  true,  rejected
			REJECTED,       SUBMIT,  false, rejected
			REJECTED,       APPROVE, true,  rejected
			REJECTED,       APPROVE, false, rejected
			REJECTED,       REJECT,  true,  rejected
			REJECTED,       REJECT,  false, rejected
			""")
	void transitionTable(VariantStatus current, VariantAction action, boolean validationPassed, String expected) {
		if (expected.equals("rejected")) {
			assertThatThrownBy(() -> current.next(action, validationPassed))
				.isInstanceOfSatisfying(IllegalTransitionException.class, ex -> {
					assertThat(ex.getCurrentStatus()).isEqualTo(current);
					assertThat(ex.getAttemptedAction()).isEqualTo(action);
				});
		}
		else {
			assertThat(current.next(action, validationPassed)).isEqualTo(VariantStatus.valueOf(expected));
		}
	}

	/** Guards the table above against a new status or action: anything not listed as allowed there is rejected. */
	@Test
	void onlyTheFiveCombinationsInTheTableAreAllowed() {
		List<String> allowed = new ArrayList<>();
		for (VariantStatus status : VariantStatus.values()) {
			for (VariantAction action : VariantAction.values()) {
				for (boolean validationPassed : new boolean[] { true, false }) {
					try {
						allowed.add(status + " " + action + " " + validationPassed + " -> "
								+ status.next(action, validationPassed));
					}
					catch (IllegalTransitionException ex) {
						// rejected, as expected for everything not listed below
					}
				}
			}
		}

		assertThat(allowed).containsExactlyInAnyOrder("DRAFT SUBMIT true -> PENDING_REVIEW",
				"PENDING_REVIEW APPROVE true -> APPROVED", "PENDING_REVIEW APPROVE false -> APPROVED",
				"PENDING_REVIEW REJECT true -> REJECTED", "PENDING_REVIEW REJECT false -> REJECTED");
	}

	@Test
	void actionFromTheWrongStatusNamesTheStatusItIsAllowedFrom() {
		assertThatThrownBy(() -> VariantStatus.DRAFT.next(VariantAction.APPROVE, true))
			.hasMessage("Can't approve a variant in status DRAFT: approve is allowed only from PENDING_REVIEW.");
		assertThatThrownBy(() -> VariantStatus.PENDING_REVIEW.next(VariantAction.SUBMIT, true))
			.hasMessage("Can't submit a variant in status PENDING_REVIEW: submit is allowed only from DRAFT.");
	}

	@Test
	void actionOnAFinalStatusSaysApprovedAndRejectedAreFinal() {
		assertThatThrownBy(() -> VariantStatus.REJECTED.next(VariantAction.APPROVE, true))
			.hasMessage("Can't approve a variant in status REJECTED: APPROVED and REJECTED are final. "
					+ "Generate a new variant for this location instead.");
		assertThatThrownBy(() -> VariantStatus.APPROVED.next(VariantAction.APPROVE, true))
			.hasMessage("Can't approve a variant in status APPROVED: APPROVED and REJECTED are final. "
					+ "Generate a new variant for this location instead.");
	}

	@Test
	void submittingAFlaggedDraftExplainsThatFlaggedVariantsCantEnterReview() {
		assertThatThrownBy(() -> VariantStatus.DRAFT.next(VariantAction.SUBMIT, false))
			.isInstanceOfSatisfying(IllegalTransitionException.class, ex -> {
				assertThat(ex.getCurrentStatus()).isEqualTo(VariantStatus.DRAFT);
				assertThat(ex.getAttemptedAction()).isEqualTo(VariantAction.SUBMIT);
			})
			.hasMessage("Can't submit a variant that failed validation: flagged variants can't enter review. "
					+ "Generate a new variant for this location instead.");
	}

	@Test
	void statusIsCheckedBeforeTheValidationFlag() {
		assertThatThrownBy(() -> VariantStatus.APPROVED.next(VariantAction.SUBMIT, false))
			.hasMessageStartingWith("Can't submit a variant in status APPROVED:");
	}

}
