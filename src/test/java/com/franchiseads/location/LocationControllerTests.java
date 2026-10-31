package com.franchiseads.location;

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
import com.franchiseads.common.error.ResourceNotFoundException;
import com.franchiseads.location.dto.CreateLocationRequest;
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

@WebMvcTest(LocationController.class)
class LocationControllerTests {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private LocationService locationService;

	@Test
	void createReturns201WithLocationAndBody() throws Exception {
		given(locationService.create(eq(3L), any())).willReturn(location(11L, 3L, "Downtown"));

		mvc.perform(post("/brands/3/locations").contentType(MediaType.APPLICATION_JSON).content("""
				{"name": "Downtown", "city": "Austin", "region": "TX", "localOffer": "Free first class"}
				"""))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "/locations/11"))
			.andExpect(jsonPath("$.id").value(11))
			.andExpect(jsonPath("$.brandId").value(3))
			.andExpect(jsonPath("$.city").value("Austin"))
			.andExpect(jsonPath("$.localOffer").value("Free first class"));
	}

	@Test
	void createStripsFieldsAndNullsBlankOptionalOnes() throws Exception {
		given(locationService.create(eq(3L), any())).willReturn(location(11L, 3L, "Downtown"));

		mvc.perform(post("/brands/3/locations").contentType(MediaType.APPLICATION_JSON).content("""
				{"name": " Downtown ", "city": " Austin", "region": "  ", "localOffer": ""}
				"""))
			.andExpect(status().isCreated());

		ArgumentCaptor<CreateLocationRequest> captor = ArgumentCaptor.forClass(CreateLocationRequest.class);
		verify(locationService).create(eq(3L), captor.capture());
		assertThat(captor.getValue()).isEqualTo(new CreateLocationRequest("Downtown", "Austin", null, null));
	}

	@Test
	void rejectsMissingRequiredAndOverlongOptionalFields() throws Exception {
		mvc.perform(post("/brands/3/locations").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \"\", \"region\": \"" + "r".repeat(101) + "\", \"localOffer\": \"" + "o".repeat(201)
					+ "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors.length()").value(4))
			.andExpect(jsonPath("$.errors[0].field").value("city"))
			.andExpect(jsonPath("$.errors[0].message").value("must not be blank"))
			.andExpect(jsonPath("$.errors[1].field").value("localOffer"))
			.andExpect(jsonPath("$.errors[1].message").value("size must be between 0 and 200"))
			.andExpect(jsonPath("$.errors[2].field").value("name"))
			.andExpect(jsonPath("$.errors[2].message").value("must not be blank"))
			.andExpect(jsonPath("$.errors[3].field").value("region"))
			.andExpect(jsonPath("$.errors[3].message").value("size must be between 0 and 100"));
		verifyNoInteractions(locationService);
	}

	@Test
	void createUnderUnknownBrandIs404() throws Exception {
		given(locationService.create(eq(99L), any())).willThrow(new ResourceNotFoundException("Brand", 99L));

		mvc.perform(post("/brands/99/locations").contentType(MediaType.APPLICATION_JSON).content("""
				{"name": "Downtown", "city": "Austin"}
				"""))
			.andExpect(status().isNotFound())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Brand 99 not found"));
	}

	@Test
	void listForBrandReturnsLocations() throws Exception {
		given(locationService.listForBrand(3L))
			.willReturn(List.of(location(11L, 3L, "Downtown"), location(12L, 3L, "Uptown")));

		mvc.perform(get("/brands/3/locations"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[1].name").value("Uptown"));
	}

	@Test
	void listForUnknownBrandIs404() throws Exception {
		given(locationService.listForBrand(99L)).willThrow(new ResourceNotFoundException("Brand", 99L));

		mvc.perform(get("/brands/99/locations"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Brand 99 not found"));
	}

	@Test
	void getReturnsLocation() throws Exception {
		given(locationService.get(11L)).willReturn(location(11L, 3L, "Downtown"));

		mvc.perform(get("/locations/11"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.brandId").value(3))
			.andExpect(jsonPath("$.name").value("Downtown"));
	}

	@Test
	void getUnknownLocationIs404() throws Exception {
		given(locationService.get(99L)).willThrow(new ResourceNotFoundException("Location", 99L));

		mvc.perform(get("/locations/99"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Location 99 not found"));
	}

	private static Location location(Long id, Long brandId, String name) {
		Brand brand = new Brand("Acme", null, List.of());
		ReflectionTestUtils.setField(brand, "id", brandId);
		Location location = new Location(brand, name, "Austin", "TX", "Free first class");
		ReflectionTestUtils.setField(location, "id", id);
		ReflectionTestUtils.setField(location, "createdAt", Instant.parse("2026-11-01T14:03:11Z"));
		return location;
	}

}
