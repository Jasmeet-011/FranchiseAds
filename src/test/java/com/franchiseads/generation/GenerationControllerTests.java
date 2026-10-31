package com.franchiseads.generation;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.franchiseads.brand.Brand;
import com.franchiseads.campaign.Campaign;
import com.franchiseads.claude.ClaudeRequestException;
import com.franchiseads.claude.ClaudeTimeoutException;
import com.franchiseads.claude.ClaudeUnavailableException;
import com.franchiseads.common.error.ResourceNotFoundException;
import com.franchiseads.location.Location;
import com.franchiseads.variant.AdVariant;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(GenerationController.class)
class GenerationControllerTests {

	/** Put into every upstream exception message; must never show up in a response body. */
	private static final String SECRET = "upstream-secret";

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private GenerationService generationService;

	@Test
	void generateReturns201WithLocationAndTheVariant() throws Exception {
		given(generationService.generate(3L, 42L)).willReturn(variant(7L, List.of(), 1));

		generate(3, "{\"locationId\": 42}")
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "/variants/7"))
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.id").value(7))
			.andExpect(jsonPath("$.campaignId").value(3))
			.andExpect(jsonPath("$.locationId").value(42))
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.validationPassed").value(true))
			.andExpect(jsonPath("$.violations.length()").value(0))
			.andExpect(jsonPath("$.headlines[0]").value("Join Today"))
			.andExpect(jsonPath("$.descriptions[0]").value("Your first month is on us."))
			.andExpect(jsonPath("$.generationAttempts").value(1))
			.andExpect(jsonPath("$.model").value("claude-haiku-5-5"))
			.andExpect(jsonPath("$.createdAt").value("2026-11-01T14:03:11Z"));
	}

	@Test
	void flaggedVariantIsStill201WithItsViolations() throws Exception {
		String violation = "EXCLAMATION_IN_HEADLINE: headlines[0] contains \"!\": \"Join Today!\"";
		given(generationService.generate(3L, 42L)).willReturn(variant(8L, List.of(violation), 2));

		generate(3, "{\"locationId\": 42}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.validationPassed").value(false))
			.andExpect(jsonPath("$.violations[0]").value(violation))
			.andExpect(jsonPath("$.generationAttempts").value(2));
	}

	@Test
	void missingLocationIdIs400() throws Exception {
		generate(3, "{}")
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors[0].field").value("locationId"))
			.andExpect(jsonPath("$.errors[0].message").value("must not be null"));
		verifyNoInteractions(generationService);
	}

	@Test
	void nonPositiveLocationIdIs400() throws Exception {
		generate(3, "{\"locationId\": 0}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("locationId"))
			.andExpect(jsonPath("$.errors[0].message").value("must be greater than 0"));
		verifyNoInteractions(generationService);
	}

	@Test
	void nonNumericCampaignIdIs400() throws Exception {
		mvc.perform(post("/campaigns/abc/generate").contentType(MediaType.APPLICATION_JSON)
			.content("{\"locationId\": 42}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
		verifyNoInteractions(generationService);
	}

	@Test
	void unknownCampaignIs404() throws Exception {
		given(generationService.generate(99L, 42L)).willThrow(new ResourceNotFoundException("Campaign", 99L));

		generate(99, "{\"locationId\": 42}")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Campaign 99 not found"));
	}

	@Test
	void locationOfAnotherBrandIs422() throws Exception {
		given(generationService.generate(3L, 42L)).willThrow(new LocationBrandMismatchException(42L, 3L));

		generate(3, "{\"locationId\": 42}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(422))
			.andExpect(jsonPath("$.detail").value("Location 42 does not belong to the brand of campaign 3"));
	}

	@ParameterizedTest(name = "{0} -> {1}")
	@MethodSource("upstreamFailures")
	void upstreamFailureMapsToFixedProblemDetailWithoutUpstreamText(RuntimeException failure, int status,
			String detail) throws Exception {
		given(generationService.generate(3L, 42L)).willThrow(failure);

		generate(3, "{\"locationId\": 42}")
			.andExpect(status().is(status))
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(status))
			.andExpect(jsonPath("$.detail").value(detail))
			.andExpect(content().string(not(containsString(SECRET))));
	}

	static Stream<Arguments> upstreamFailures() {
		return Stream.of(
				Arguments.of(new MalformedAdCopyException("both malformed: " + SECRET), 502,
						"The AI model did not return usable ad copy. Try again."),
				Arguments.of(new ClaudeRequestException("HTTP 401 authentication_error: " + SECRET), 502,
						"The AI provider rejected the request."),
				Arguments.of(new ClaudeUnavailableException("HTTP 529 overloaded_error: " + SECRET, null), 503,
						"The AI provider is unavailable. Try again later."),
				Arguments.of(new ClaudeTimeoutException("Claude deadline reached: " + SECRET, null), 504,
						"The AI provider did not respond in time. Try again later."));
	}

	private ResultActions generate(long campaignId, String body) throws Exception {
		return mvc.perform(post("/campaigns/{campaignId}/generate", campaignId).contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private static AdVariant variant(Long id, List<String> violations, int attempts) {
		Brand brand = new Brand("Acme", null, List.of());
		Campaign campaign = new Campaign(brand, "Spring", "New year");
		ReflectionTestUtils.setField(campaign, "id", 3L);
		Location location = new Location(brand, "Downtown", "Austin", null, null);
		ReflectionTestUtils.setField(location, "id", 42L);
		AdVariant variant = new AdVariant(campaign, location, List.of("Join Today"),
				List.of("Your first month is on us."), violations.isEmpty(), violations, attempts, "claude-haiku-5-5");
		ReflectionTestUtils.setField(variant, "id", id);
		ReflectionTestUtils.setField(variant, "createdAt", Instant.parse("2026-11-01T14:03:11Z"));
		return variant;
	}

}
