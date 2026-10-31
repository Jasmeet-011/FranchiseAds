package com.franchiseads.variant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import com.franchiseads.TestcontainersConfiguration;
import com.franchiseads.TruncateTablesExtension;
import com.franchiseads.brand.Brand;
import com.franchiseads.brand.BrandRepository;
import com.franchiseads.campaign.Campaign;
import com.franchiseads.campaign.CampaignRepository;
import com.franchiseads.location.Location;
import com.franchiseads.location.LocationRepository;
import com.franchiseads.variant.dto.VariantResponse;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the {@code @Version} check on a review action over HTTP, deterministically and without threads (ADR-033).
 * The spied repository lets the test act between the request's read and its write: the reject request loads the
 * variant at version 1, then a separate transaction approves it and commits (version 2), and only then does the
 * request apply its reject to the stale entity and flush.
 */
@Import(TestcontainersConfiguration.class)
@ExtendWith(TruncateTablesExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class VariantOptimisticLockingTests {

	@Autowired
	private RestTestClient client;

	@Autowired
	private VariantService variantService;

	@Autowired
	private BrandRepository brands;

	@Autowired
	private LocationRepository locations;

	@Autowired
	private CampaignRepository campaigns;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoSpyBean
	private AdVariantRepository variants;

	private Long variantId;

	@BeforeEach
	void createSubmittedVariant() {
		Brand brand = brands.save(new Brand("Locking Gym", null, List.of()));
		Location location = locations.save(new Location(brand, "Downtown", "Austin", "TX", null));
		Campaign campaign = campaigns.save(new Campaign(brand, "Spring", "New year, new routine"));
		variantId = variantService.saveGenerated(new GeneratedVariant(campaign.getId(), location.getId(),
				List.of("Join Today"), List.of("Your first month is on us."), List.of(), 1, "claude-haiku-5-5"))
			.getId();
		variantService.submit(variantId); // version 0 on insert, 1 after submit
	}

	@Test
	void staleRejectAfterAConcurrentApproveIs409AndDoesNotLand() {
		TransactionTemplate separateTransaction = new TransactionTemplate(transactionManager);
		separateTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		AtomicBoolean approvedInBetween = new AtomicBoolean();
		doAnswer(invocation -> {
			// Runs inside whichever transaction calls findById: first the reject request's, then the approve's.
			AdVariant loaded = entityManager.find(AdVariant.class, variantId);
			if (approvedInBetween.compareAndSet(false, true)) {
				// The reject request now holds the variant at version 1. Approve it elsewhere and commit.
				separateTransaction.executeWithoutResult(status -> variantService.approve(variantId));
			}
			return Optional.ofNullable(loaded);
		}).when(variants).findById(variantId);

		client.post()
			.uri("/variants/{id}/reject", variantId)
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"reason\": \"Off-brand tone\"}")
			.exchange()
			.expectStatus().isEqualTo(409)
			.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.expectBody()
			.jsonPath("$.detail").isEqualTo("The variant was modified concurrently. Reload it and retry.");

		assertThat(approvedInBetween).isTrue();
		VariantResponse stored = fetch();
		assertThat(stored.status()).isEqualTo(VariantStatus.APPROVED);
		assertThat(stored.rejectionReason()).isNull();
		assertThat(storedVersion()).isEqualTo(2L);
	}

	/** Control: the same request without the change in between succeeds, so the 409 above comes from the version. */
	@Test
	void rejectWithoutAChangeInBetweenSucceeds() {
		client.post()
			.uri("/variants/{id}/reject", variantId)
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"reason\": \"Off-brand tone\"}")
			.exchange()
			.expectStatus().isOk();

		VariantResponse stored = fetch();
		assertThat(stored.status()).isEqualTo(VariantStatus.REJECTED);
		assertThat(stored.rejectionReason()).isEqualTo("Off-brand tone");
		assertThat(storedVersion()).isEqualTo(2L);
	}

	private VariantResponse fetch() {
		return client.get()
			.uri("/variants/{id}", variantId)
			.exchange()
			.expectStatus().isOk()
			.returnResult(VariantResponse.class)
			.getResponseBody();
	}

	private Long storedVersion() {
		return jdbc.queryForObject("select version from ad_variant where id = ?", Long.class, variantId);
	}

}
