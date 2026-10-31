package com.franchiseads.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.franchiseads.brand.Brand;
import com.franchiseads.campaign.dto.CreateCampaignRequest;
import com.franchiseads.common.error.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CampaignController.class)
class CampaignControllerTests {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private CampaignService campaignService;

	@Test
	void createReturns201WithLocationAndBody() throws Exception {
		given(campaignService.create(eq(3L), any())).willReturn(campaign(21L, 3L, "Spring"));

		mvc.perform(post("/brands/3/campaigns").contentType(MediaType.APPLICATION_JSON).content("""
				{"name": "Spring", "theme": "New season, new you"}
				"""))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "/campaigns/21"))
			.andExpect(jsonPath("$.id").value(21))
			.andExpect(jsonPath("$.brandId").value(3))
			.andExpect(jsonPath("$.theme").value("New season, new you"));
	}

	@Test
	void createStripsFields() throws Exception {
		given(campaignService.create(eq(3L), any())).willReturn(campaign(21L, 3L, "Spring"));

		mvc.perform(post("/brands/3/campaigns").contentType(MediaType.APPLICATION_JSON).content("""
				{"name": " Spring ", "theme": " New season, new you "}
				"""))
			.andExpect(status().isCreated());

		ArgumentCaptor<CreateCampaignRequest> captor = ArgumentCaptor.forClass(CreateCampaignRequest.class);
		verify(campaignService).create(eq(3L), captor.capture());
		assertThat(captor.getValue()).isEqualTo(new CreateCampaignRequest("Spring", "New season, new you"));
	}

	@Test
	void rejectsBlankNameAndOverlongTheme() throws Exception {
		mvc.perform(post("/brands/3/campaigns").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \" \", \"theme\": \"" + "t".repeat(501) + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors.length()").value(2))
			.andExpect(jsonPath("$.errors[0].field").value("name"))
			.andExpect(jsonPath("$.errors[0].message").value("must not be blank"))
			.andExpect(jsonPath("$.errors[1].field").value("theme"))
			.andExpect(jsonPath("$.errors[1].message").value("size must be between 0 and 500"));
		verifyNoInteractions(campaignService);
	}

	@Test
	void rejectsMissingTheme() throws Exception {
		mvc.perform(post("/brands/3/campaigns").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \"Spring\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("theme"))
			.andExpect(jsonPath("$.errors[0].message").value("must not be blank"));
	}

	@Test
	void createUnderUnknownBrandIs404() throws Exception {
		given(campaignService.create(eq(99L), any())).willThrow(new ResourceNotFoundException("Brand", 99L));

		mvc.perform(post("/brands/99/campaigns").contentType(MediaType.APPLICATION_JSON).content("""
				{"name": "Spring", "theme": "New season, new you"}
				"""))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Brand 99 not found"));
	}

	@Test
	void listForBrandReturnsCampaigns() throws Exception {
		given(campaignService.listForBrand(3L))
			.willReturn(List.of(campaign(21L, 3L, "Spring"), campaign(22L, 3L, "Summer")));

		mvc.perform(get("/brands/3/campaigns"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[1].name").value("Summer"));
	}

	@Test
	void listForUnknownBrandIs404() throws Exception {
		given(campaignService.listForBrand(99L)).willThrow(new ResourceNotFoundException("Brand", 99L));

		mvc.perform(get("/brands/99/campaigns"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Brand 99 not found"));
	}

	@Test
	void getReturnsCampaign() throws Exception {
		given(campaignService.get(21L)).willReturn(campaign(21L, 3L, "Spring"));

		mvc.perform(get("/campaigns/21"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.brandId").value(3))
			.andExpect(jsonPath("$.name").value("Spring"));
	}

	@Test
	void getUnknownCampaignIs404() throws Exception {
		given(campaignService.get(99L)).willThrow(new ResourceNotFoundException("Campaign", 99L));

		mvc.perform(get("/campaigns/99"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Campaign 99 not found"));
	}

	private static Campaign campaign(Long id, Long brandId, String name) {
		Brand brand = new Brand("Acme", null, List.of());
		ReflectionTestUtils.setField(brand, "id", brandId);
		Campaign campaign = new Campaign(brand, name, "New season, new you");
		ReflectionTestUtils.setField(campaign, "id", id);
		ReflectionTestUtils.setField(campaign, "createdAt", Instant.parse("2026-11-01T14:03:11Z"));
		return campaign;
	}

}
