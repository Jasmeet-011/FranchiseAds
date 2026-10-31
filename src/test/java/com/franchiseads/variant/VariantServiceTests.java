package com.franchiseads.variant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.franchiseads.campaign.Campaign;
import com.franchiseads.campaign.CampaignService;
import com.franchiseads.common.error.ResourceNotFoundException;
import com.franchiseads.location.Location;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class VariantServiceTests {

	private final AdVariantRepository variants = mock(AdVariantRepository.class);

	private final CampaignService campaignService = mock(CampaignService.class);

	private final EntityManager entityManager = mock(EntityManager.class);

	private final VariantService service = new VariantService(variants, campaignService, entityManager);

	private final Campaign campaign = mock(Campaign.class);

	private final Location location = mock(Location.class);

	@Test
	void savesValidCopyAsPassingDraftReferencingCampaignAndLocation() {
		stubReferencesAndSave();

		AdVariant variant = service.saveGenerated(new GeneratedVariant(3L, 42L, List.of("h1", "h2"), List.of("d1"),
				List.of(), 1, "claude-haiku-5-5"));

		assertThat(variant.getCampaign()).isSameAs(campaign);
		assertThat(variant.getLocation()).isSameAs(location);
		assertThat(variant.getHeadlines()).containsExactly("h1", "h2");
		assertThat(variant.getDescriptions()).containsExactly("d1");
		assertThat(variant.getStatus()).isEqualTo(VariantStatus.DRAFT);
		assertThat(variant.isValidationPassed()).isTrue();
		assertThat(variant.getViolations()).isEmpty();
		assertThat(variant.getGenerationAttempts()).isEqualTo(1);
		assertThat(variant.getModel()).isEqualTo("claude-haiku-5-5");
	}

	@Test
	void savesCopyWithViolationsAsFlaggedDraft() {
		stubReferencesAndSave();

		AdVariant variant = service.saveGenerated(new GeneratedVariant(3L, 42L, List.of("h1!"), List.of("d1"),
				List.of("EXCLAMATION_IN_HEADLINE: headlines[0] contains \"!\": \"h1!\""), 2, "claude-haiku-5-5"));

		assertThat(variant.getStatus()).isEqualTo(VariantStatus.DRAFT);
		assertThat(variant.isValidationPassed()).isFalse();
		assertThat(variant.getViolations())
			.containsExactly("EXCLAMATION_IN_HEADLINE: headlines[0] contains \"!\": \"h1!\"");
		assertThat(variant.getGenerationAttempts()).isEqualTo(2);
	}

	@Test
	void listsEveryVariantOfTheCampaignWithoutAStatus() {
		AdVariant first = mock(AdVariant.class);
		AdVariant second = mock(AdVariant.class);
		when(variants.findAllByCampaignIdOrderByIdAsc(3L)).thenReturn(List.of(first, second));

		assertThat(service.listForCampaign(3L, null)).containsExactly(first, second);
	}

	@Test
	void listsOnlyVariantsInTheRequestedStatus() {
		AdVariant draft = mock(AdVariant.class);
		when(variants.findAllByCampaignIdAndStatusOrderByIdAsc(3L, VariantStatus.DRAFT)).thenReturn(List.of(draft));

		assertThat(service.listForCampaign(3L, VariantStatus.DRAFT)).containsExactly(draft);
	}

	@Test
	void listForUnknownCampaignIs404WithoutQueryingVariants() {
		when(campaignService.get(99L)).thenThrow(new ResourceNotFoundException("Campaign", 99L));

		assertThatThrownBy(() -> service.listForCampaign(99L, null)).isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Campaign 99 not found");
		verifyNoInteractions(variants);
	}

	@Test
	void getUnknownVariantIs404() {
		when(variants.findById(5L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(5L)).isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Variant 5 not found");
	}

	@Test
	void submitMovesTheStoredDraftToPendingReviewAndFlushes() {
		AdVariant variant = storedVariant(7L, true);

		AdVariant submitted = service.submit(7L);

		assertThat(submitted.getStatus()).isEqualTo(VariantStatus.PENDING_REVIEW);
		verify(variants).saveAndFlush(variant);
	}

	@Test
	void approveMovesAPendingVariantToApprovedAndFlushes() {
		AdVariant variant = storedVariant(7L, true);
		variant.submit();

		AdVariant approved = service.approve(7L);

		assertThat(approved.getStatus()).isEqualTo(VariantStatus.APPROVED);
		verify(variants).saveAndFlush(variant);
	}

	@Test
	void rejectStoresTheReasonAndFlushes() {
		AdVariant variant = storedVariant(7L, true);
		variant.submit();

		AdVariant rejected = service.reject(7L, "Off-brand tone");

		assertThat(rejected.getStatus()).isEqualTo(VariantStatus.REJECTED);
		assertThat(rejected.getRejectionReason()).isEqualTo("Off-brand tone");
		verify(variants).saveAndFlush(variant);
	}

	@Test
	void refusedTransitionPropagatesAndNothingIsSaved() {
		storedVariant(7L, true);

		assertThatThrownBy(() -> service.approve(7L)).isInstanceOf(IllegalTransitionException.class);

		verify(variants, never()).saveAndFlush(any());
	}

	@Test
	void flaggedVariantIsNotSubmittedAndNothingIsSaved() {
		AdVariant variant = storedVariant(8L, false);

		assertThatThrownBy(() -> service.submit(8L)).isInstanceOf(IllegalTransitionException.class);

		assertThat(variant.getStatus()).isEqualTo(VariantStatus.DRAFT);
		verify(variants, never()).saveAndFlush(any());
	}

	@ParameterizedTest
	@EnumSource(VariantAction.class)
	void actionOnUnknownVariantIs404AndNothingIsSaved(VariantAction action) {
		when(variants.findById(5L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> {
			switch (action) {
				case SUBMIT -> service.submit(5L);
				case APPROVE -> service.approve(5L);
				case REJECT -> service.reject(5L, "Off-brand tone");
			}
		}).isInstanceOf(ResourceNotFoundException.class).hasMessage("Variant 5 not found");
		verify(variants, never()).saveAndFlush(any());
	}

	private AdVariant storedVariant(Long id, boolean validationPassed) {
		AdVariant variant = new AdVariant(campaign, location, List.of("Join Today"),
				List.of("Your first month is on us."), validationPassed,
				validationPassed ? List.of() : List.of("BLANK: headlines[1] is blank"), 1, "claude-haiku-5-5");
		when(variants.findById(id)).thenReturn(Optional.of(variant));
		when(variants.saveAndFlush(any(AdVariant.class))).thenAnswer(invocation -> invocation.getArgument(0));
		return variant;
	}

	private void stubReferencesAndSave() {
		when(entityManager.getReference(Campaign.class, 3L)).thenReturn(campaign);
		when(entityManager.getReference(Location.class, 42L)).thenReturn(location);
		when(variants.save(any(AdVariant.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

}
