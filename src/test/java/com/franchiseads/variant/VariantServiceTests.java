package com.franchiseads.variant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.franchiseads.campaign.Campaign;
import com.franchiseads.location.Location;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;

class VariantServiceTests {

	private final AdVariantRepository variants = mock(AdVariantRepository.class);

	private final EntityManager entityManager = mock(EntityManager.class);

	private final VariantService service = new VariantService(variants, entityManager);

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

	private void stubReferencesAndSave() {
		when(entityManager.getReference(Campaign.class, 3L)).thenReturn(campaign);
		when(entityManager.getReference(Location.class, 42L)).thenReturn(location);
		when(variants.save(any(AdVariant.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

}
