package com.franchiseads.variant;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.franchiseads.brand.Brand;
import com.franchiseads.campaign.Campaign;
import com.franchiseads.common.error.ResourceNotFoundException;
import com.franchiseads.location.Location;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VariantController.class)
class VariantControllerTests {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private VariantService variantService;

	@Test
	void listsEveryVariantOfACampaignWithoutAStatus() throws Exception {
		given(variantService.listForCampaign(3L, null)).willReturn(List.of(variant(7L, true), variant(8L, false)));

		mvc.perform(get("/campaigns/3/variants"))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].id").value(7))
			.andExpect(jsonPath("$[0].campaignId").value(3))
			.andExpect(jsonPath("$[0].validationPassed").value(true))
			.andExpect(jsonPath("$[1].id").value(8))
			.andExpect(jsonPath("$[1].validationPassed").value(false))
			.andExpect(jsonPath("$[1].violations[0]").value("BLANK: headlines[0] is blank"));
	}

	@Test
	void filtersByStatus() throws Exception {
		given(variantService.listForCampaign(3L, VariantStatus.DRAFT)).willReturn(List.of(variant(7L, true)));

		mvc.perform(get("/campaigns/3/variants").param("status", "DRAFT"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].status").value("DRAFT"));
	}

	@Test
	void unknownStatusIs400() throws Exception {
		mvc.perform(get("/campaigns/3/variants").param("status", "PUBLISHED"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.detail").value(containsString("status")));
		verifyNoInteractions(variantService);
	}

	/** Pins the framework's behavior: status values are the exact enum names. */
	@Test
	void lowercaseStatusIs400() throws Exception {
		mvc.perform(get("/campaigns/3/variants").param("status", "draft"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
		verifyNoInteractions(variantService);
	}

	@Test
	void listForUnknownCampaignIs404() throws Exception {
		given(variantService.listForCampaign(99L, null)).willThrow(new ResourceNotFoundException("Campaign", 99L));

		mvc.perform(get("/campaigns/99/variants"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Campaign 99 not found"));
	}

	@Test
	void getReturnsTheVariant() throws Exception {
		given(variantService.get(7L)).willReturn(variant(7L, true));

		mvc.perform(get("/variants/7"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(7))
			.andExpect(jsonPath("$.locationId").value(42))
			.andExpect(jsonPath("$.headlines[0]").value("Join Today"))
			.andExpect(jsonPath("$.generationAttempts").value(1))
			.andExpect(jsonPath("$.model").value("claude-haiku-5-5"))
			.andExpect(jsonPath("$.createdAt").value("2026-11-01T14:03:11Z"));
	}

	@Test
	void getUnknownVariantIs404() throws Exception {
		given(variantService.get(anyLong())).willThrow(new ResourceNotFoundException("Variant", 5L));

		mvc.perform(get("/variants/5"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Variant 5 not found"));
	}

	@Test
	void nonNumericVariantIdIs400() throws Exception {
		mvc.perform(get("/variants/abc"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
		verifyNoInteractions(variantService);
	}

	private static AdVariant variant(Long id, boolean valid) {
		Brand brand = new Brand("Acme", null, List.of());
		Campaign campaign = new Campaign(brand, "Spring", "New year");
		ReflectionTestUtils.setField(campaign, "id", 3L);
		Location location = new Location(brand, "Downtown", "Austin", null, null);
		ReflectionTestUtils.setField(location, "id", 42L);
		List<String> violations = valid ? List.of() : List.of("BLANK: headlines[0] is blank");
		AdVariant variant = new AdVariant(campaign, location, List.of(valid ? "Join Today" : ""),
				List.of("Your first month is on us."), valid, violations, valid ? 1 : 2, "claude-haiku-5-5");
		ReflectionTestUtils.setField(variant, "id", id);
		ReflectionTestUtils.setField(variant, "createdAt", Instant.parse("2026-11-01T14:03:11Z"));
		return variant;
	}

}
