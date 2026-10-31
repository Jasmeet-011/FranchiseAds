package com.franchiseads.variant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

	private void stubReferencesAndSave() {
		when(entityManager.getReference(Campaign.class, 3L)).thenReturn(campaign);
		when(entityManager.getReference(Location.class, 42L)).thenReturn(location);
		when(variants.save(any(AdVariant.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

}
