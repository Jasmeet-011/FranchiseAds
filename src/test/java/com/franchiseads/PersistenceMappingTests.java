package com.franchiseads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.franchiseads.brand.Brand;
import com.franchiseads.brand.BrandRepository;
import com.franchiseads.campaign.Campaign;
import com.franchiseads.campaign.CampaignRepository;
import com.franchiseads.location.Location;
import com.franchiseads.location.LocationRepository;
import com.franchiseads.variant.AdVariant;
import com.franchiseads.variant.AdVariantRepository;
import com.franchiseads.variant.VariantStatus;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Runs Flyway V1 against real Postgres with ddl-auto=validate, then proves the mappings round-trip.
 * Hibernate's schema validation does not check text[] column types (ADR-016), so these round trips are
 * what guard the array mappings.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class PersistenceMappingTests {

	@Autowired
	private BrandRepository brands;

	@Autowired
	private LocationRepository locations;

	@Autowired
	private CampaignRepository campaigns;

	@Autowired
	private AdVariantRepository variants;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void bannedWordsRoundTripAsTextArray() {
		Long id = brands.saveAndFlush(new Brand("Acme Fitness", "Upbeat", List.of("cheap", "best in town")))
			.getId();
		entityManager.clear();

		assertThat(brands.findById(id).orElseThrow().getBannedWords()).containsExactly("cheap", "best in town");
		assertThat(jdbc.queryForObject("select pg_typeof(banned_words)::text from brand where id = ?", String.class,
				id))
			.isEqualTo("text[]");
	}

	@Test
	void emptyBannedWordsRoundTripAsEmptyListNotNull() {
		Long id = brands.saveAndFlush(new Brand("Empty Words", null, List.of())).getId();
		entityManager.clear();

		assertThat(brands.findById(id).orElseThrow().getBannedWords()).isNotNull().isEmpty();
		assertThat(jdbc.queryForObject("select cardinality(banned_words) from brand where id = ?", Integer.class, id))
			.isZero();
	}

	@Test
	void brandNamesConflictIgnoringCase() {
		brands.saveAndFlush(new Brand("Acme", null, List.of()));

		assertThatThrownBy(() -> brands.saveAndFlush(new Brand("acme", null, List.of())))
			.isInstanceOf(DataIntegrityViolationException.class)
			.cause()
			.isInstanceOfSatisfying(ConstraintViolationException.class,
					cve -> assertThat(cve.getConstraintName()).isEqualTo("uq_brand_name_lower"));
	}

	@Test
	void locationAndCampaignReferenceTheirBrand() {
		Brand brand = brands.saveAndFlush(new Brand("Ref Brand", null, List.of()));
		Long locationId = locations.saveAndFlush(new Location(brand, "Downtown", "Austin", "TX", "Free first class"))
			.getId();
		Long campaignId = campaigns.saveAndFlush(new Campaign(brand, "Spring", "New year, new you")).getId();
		entityManager.clear();

		Location location = locations.findById(locationId).orElseThrow();
		assertThat(location.getBrand().getId()).isEqualTo(brand.getId());
		assertThat(location.getRegion()).isEqualTo("TX");
		assertThat(campaigns.findById(campaignId).orElseThrow().getBrand().getId()).isEqualTo(brand.getId());
	}

	@Test
	void adVariantRoundTripsArraysAndStoresStatusByName() {
		Brand brand = brands.saveAndFlush(new Brand("Variant Brand", null, List.of()));
		Location location = locations.saveAndFlush(new Location(brand, "Uptown", "Dallas", null, null));
		Campaign campaign = campaigns.saveAndFlush(new Campaign(brand, "Summer", "Beat the heat"));
		List<String> headlines = List.of("Join Today", "Dallas Gym", "Open 24/7", "First Class Free", "Get Strong");
		List<String> descriptions = List.of("Classes for every level.", "Visit Uptown Dallas this week.");

		Long passedId = variants.saveAndFlush(new AdVariant(campaign, location, headlines, descriptions, true,
				List.of(), 1, "claude-haiku-5-5"))
			.getId();
		Long flaggedId = variants.saveAndFlush(new AdVariant(campaign, location, headlines, descriptions, false,
				List.of("HEADLINE_TOO_LONG: headlines[2] is 34 characters (max 30)"), 2, "claude-haiku-5-5"))
			.getId();
		entityManager.clear();

		AdVariant passed = variants.findById(passedId).orElseThrow();
		assertThat(passed.getHeadlines()).containsExactlyElementsOf(headlines);
		assertThat(passed.getDescriptions()).containsExactlyElementsOf(descriptions);
		assertThat(passed.getViolations()).isNotNull().isEmpty();
		assertThat(passed.getStatus()).isEqualTo(VariantStatus.DRAFT);
		assertThat(passed.getVersion()).isZero();
		assertThat(variants.findById(flaggedId).orElseThrow().getViolations())
			.containsExactly("HEADLINE_TOO_LONG: headlines[2] is 34 characters (max 30)");
		assertThat(jdbc.queryForObject("select status from ad_variant where id = ?", String.class, passedId))
			.isEqualTo("DRAFT");
	}

}
