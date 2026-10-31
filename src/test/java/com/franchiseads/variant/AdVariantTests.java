package com.franchiseads.variant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The entity applies {@link VariantStatus#next}; these check what it stores, not the rules themselves. */
class AdVariantTests {

	@Test
	void validDraftGoesThroughSubmitAndApprove() {
		AdVariant variant = variant(true);

		variant.submit();
		assertThat(variant.getStatus()).isEqualTo(VariantStatus.PENDING_REVIEW);
		variant.approve();

		assertThat(variant.getStatus()).isEqualTo(VariantStatus.APPROVED);
		assertThat(variant.getRejectionReason()).isNull();
	}

	@Test
	void rejectStoresTheReason() {
		AdVariant variant = variant(true);
		variant.submit();

		variant.reject("Off-brand tone");

		assertThat(variant.getStatus()).isEqualTo(VariantStatus.REJECTED);
		assertThat(variant.getRejectionReason()).isEqualTo("Off-brand tone");
	}

	@Test
	void rejectNotAllowedFromDraftLeavesStatusAndReasonUnchanged() {
		AdVariant variant = variant(true);

		assertThatThrownBy(() -> variant.reject("Off-brand tone")).isInstanceOf(IllegalTransitionException.class);

		assertThat(variant.getStatus()).isEqualTo(VariantStatus.DRAFT);
		assertThat(variant.getRejectionReason()).isNull();
	}

	@Test
	void flaggedDraftCannotBeSubmittedAndStaysDraft() {
		AdVariant variant = variant(false);

		assertThatThrownBy(variant::submit).isInstanceOf(IllegalTransitionException.class);

		assertThat(variant.getStatus()).isEqualTo(VariantStatus.DRAFT);
	}

	@Test
	void approvingTwiceIsRejectedAndTheVariantStaysApproved() {
		AdVariant variant = variant(true);
		variant.submit();
		variant.approve();

		assertThatThrownBy(variant::approve).isInstanceOf(IllegalTransitionException.class);

		assertThat(variant.getStatus()).isEqualTo(VariantStatus.APPROVED);
	}

	private static AdVariant variant(boolean validationPassed) {
		return new AdVariant(null, null, List.of("Join Today"), List.of("Your first month is on us."),
				validationPassed, validationPassed ? List.of() : List.of("BLANK: headlines[1] is blank"), 1,
				"claude-haiku-5-5");
	}

}
