package com.franchiseads.variant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
			.andExpect(jsonPath("$.rejectionReason").value(nullValue()))
			.andExpect(jsonPath("$.createdAt").value("2026-11-01T14:03:11Z"))
			.andExpect(jsonPath("$.updatedAt").value("2026-11-01T14:05:00Z"));
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

	@Test
	void submitReturnsTheVariantInPendingReview() throws Exception {
		AdVariant variant = variant(7L, true);
		variant.submit();
		given(variantService.submit(7L)).willReturn(variant);

		mvc.perform(post("/variants/7/submit"))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.id").value(7))
			.andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
			.andExpect(jsonPath("$.rejectionReason").value(nullValue()));
	}

	@Test
	void approveReturnsTheApprovedVariant() throws Exception {
		AdVariant variant = variant(7L, true);
		variant.submit();
		variant.approve();
		given(variantService.approve(7L)).willReturn(variant);

		mvc.perform(post("/variants/7/approve"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPROVED"))
			.andExpect(jsonPath("$.rejectionReason").value(nullValue()));
	}

	@Test
	void rejectPassesTheStrippedReasonAndReturnsTheRejectedVariant() throws Exception {
		AdVariant variant = variant(7L, true);
		variant.submit();
		variant.reject("Off-brand tone");
		given(variantService.reject(7L, "Off-brand tone")).willReturn(variant);

		mvc.perform(post("/variants/7/reject").contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\": \"  Off-brand tone \\n\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REJECTED"))
			.andExpect(jsonPath("$.rejectionReason").value("Off-brand tone"));
		verify(variantService).reject(7L, "Off-brand tone");
	}

	@Test
	void rejectAcceptsAReasonOfExactly500Characters() throws Exception {
		String reason = "x".repeat(500);
		AdVariant variant = variant(7L, true);
		variant.submit();
		variant.reject(reason);
		given(variantService.reject(7L, reason)).willReturn(variant);

		mvc.perform(post("/variants/7/reject").contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\": \"" + reason + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rejectionReason").value(reason));
	}

	@ParameterizedTest
	@ValueSource(strings = { "{}", "{\"reason\": null}", "{\"reason\": \"\"}", "{\"reason\": \"   \"}" })
	void rejectWithoutAReasonIs400NamingTheField(String body) throws Exception {
		mvc.perform(post("/variants/7/reject").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors.length()").value(1))
			.andExpect(jsonPath("$.errors[0].field").value("reason"))
			.andExpect(jsonPath("$.errors[0].message").value("must not be blank"));
		verifyNoInteractions(variantService);
	}

	/** 501 characters inside surrounding spaces: the limit applies after stripping, and this is still too long. */
	@Test
	void rejectWithAReasonOver500CharactersIs400() throws Exception {
		mvc.perform(post("/variants/7/reject").contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\": \" " + "x".repeat(501) + " \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.length()").value(1))
			.andExpect(jsonPath("$.errors[0].field").value("reason"))
			.andExpect(jsonPath("$.errors[0].message").value("size must be between 0 and 500"));
		verifyNoInteractions(variantService);
	}

	@Test
	void rejectWithoutABodyIs400() throws Exception {
		mvc.perform(post("/variants/7/reject").contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
		verifyNoInteractions(variantService);
	}

	@Test
	void illegalTransitionIs409WithCurrentStatusAndAttemptedAction() throws Exception {
		given(variantService.approve(7L)).willThrow(refused(VariantStatus.REJECTED, VariantAction.APPROVE, true));

		mvc.perform(post("/variants/7/approve"))
			.andExpect(status().isConflict())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(409))
			.andExpect(jsonPath("$.detail").value("Can't approve a variant in status REJECTED: APPROVED and REJECTED "
					+ "are final. Generate a new variant for this location instead."))
			.andExpect(jsonPath("$.instance").value("/variants/7/approve"))
			.andExpect(jsonPath("$.currentStatus").value("REJECTED"))
			.andExpect(jsonPath("$.attemptedAction").value("APPROVE"));
	}

	@Test
	void submittingAFlaggedVariantIs409ExplainingWhy() throws Exception {
		given(variantService.submit(8L)).willThrow(refused(VariantStatus.DRAFT, VariantAction.SUBMIT, false));

		mvc.perform(post("/variants/8/submit"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Can't submit a variant that failed validation: flagged variants "
					+ "can't enter review. Generate a new variant for this location instead."))
			.andExpect(jsonPath("$.currentStatus").value("DRAFT"))
			.andExpect(jsonPath("$.attemptedAction").value("SUBMIT"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "submit", "approve" })
	void actionOnUnknownVariantIs404(String action) throws Exception {
		given(variantService.submit(5L)).willThrow(new ResourceNotFoundException("Variant", 5L));
		given(variantService.approve(5L)).willThrow(new ResourceNotFoundException("Variant", 5L));

		mvc.perform(post("/variants/5/" + action))
			.andExpect(status().isNotFound())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Variant 5 not found"));
	}

	@Test
	void rejectOnUnknownVariantIs404() throws Exception {
		given(variantService.reject(5L, "Off-brand tone")).willThrow(new ResourceNotFoundException("Variant", 5L));

		mvc.perform(post("/variants/5/reject").contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\": \"Off-brand tone\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Variant 5 not found"));
	}

	private static IllegalTransitionException refused(VariantStatus status, VariantAction action,
			boolean validationPassed) {
		try {
			status.next(action, validationPassed);
		}
		catch (IllegalTransitionException ex) {
			return ex;
		}
		throw new AssertionError(status + " + " + action + " was expected to be refused");
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
		ReflectionTestUtils.setField(variant, "updatedAt", Instant.parse("2026-11-01T14:05:00Z"));
		return variant;
	}

}
