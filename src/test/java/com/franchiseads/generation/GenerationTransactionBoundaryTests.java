package com.franchiseads.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.franchiseads.TestcontainersConfiguration;
import com.franchiseads.TruncateTablesExtension;
import com.franchiseads.brand.Brand;
import com.franchiseads.brand.BrandRepository;
import com.franchiseads.campaign.Campaign;
import com.franchiseads.campaign.CampaignRepository;
import com.franchiseads.claude.ClaudeClient;
import com.franchiseads.claude.ClaudeResponse;
import com.franchiseads.location.Location;
import com.franchiseads.location.LocationRepository;
import com.franchiseads.variant.AdVariant;
import com.franchiseads.variant.AdVariantRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves ADR-012 against the real Spring proxies and Postgres: no transaction is open while Claude is called, and the
 * variant is still saved. The mocked client records the transaction state at the moment of each call.
 */
@Import(TestcontainersConfiguration.class)
@ExtendWith(TruncateTablesExtension.class)
@SpringBootTest
class GenerationTransactionBoundaryTests {

	private static final String VALID = """
			{"headlines": ["Acme Fitness Austin", "First Month Free", "Join Today", "Classes Every Morning",
			  "Friendly Coaches"],
			 "descriptions": ["Strength, cardio and yoga in downtown Austin.", "Your first month is on us."]}""";

	@MockitoBean
	private ClaudeClient claude;

	@Autowired
	private GenerationService service;

	@Autowired
	private BrandRepository brands;

	@Autowired
	private LocationRepository locations;

	@Autowired
	private CampaignRepository campaigns;

	@Autowired
	private AdVariantRepository variants;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private final List<Boolean> transactionActive = new CopyOnWriteArrayList<>();

	private final List<Boolean> synchronizationActive = new CopyOnWriteArrayList<>();

	private Long campaignId;

	private Long locationId;

	@BeforeEach
	void createBrandLocationAndCampaign() {
		Brand brand = brands.save(new Brand("Tx Boundary " + UUID.randomUUID(), null, List.of()));
		locationId = locations.save(new Location(brand, "Downtown", "Austin", "TX", null)).getId();
		campaignId = campaigns.save(new Campaign(brand, "Spring", "New year, new routine")).getId();
	}

	@Test
	void noTransactionIsActiveDuringTheOnlyClaudeCall() {
		when(claude.complete(anyString(), anyString(), any())).thenAnswer(recordingTransactionState(VALID));

		AdVariant variant = service.generate(campaignId, locationId);

		assertThat(transactionActive).containsExactly(false);
		assertThat(synchronizationActive).containsExactly(false);
		AdVariant stored = variants.findById(variant.getId()).orElseThrow();
		assertThat(stored.isValidationPassed()).isTrue();
		assertThat(stored.getGenerationAttempts()).isEqualTo(1);
	}

	@Test
	void noTransactionIsActiveDuringEitherCallWhenRegenerating() {
		when(claude.complete(anyString(), anyString(), any()))
			.thenAnswer(recordingTransactionState(VALID.replace("Join Today", "Join Today!")))
			.thenAnswer(recordingTransactionState(VALID));

		AdVariant variant = service.generate(campaignId, locationId);

		assertThat(transactionActive).containsExactly(false, false);
		assertThat(synchronizationActive).containsExactly(false, false);
		assertThat(variants.findById(variant.getId()).orElseThrow().getGenerationAttempts()).isEqualTo(2);
	}

	/** Control: the probe used above does see a transaction when one is open, so its {@code false} means something. */
	@Test
	void probeDetectsAnOpenTransaction() {
		Boolean active = new TransactionTemplate(transactionManager)
			.execute(status -> TransactionSynchronizationManager.isActualTransactionActive());

		assertThat(active).isTrue();
	}

	private Answer<ClaudeResponse> recordingTransactionState(String text) {
		return invocation -> {
			transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive());
			synchronizationActive.add(TransactionSynchronizationManager.isSynchronizationActive());
			return new ClaudeResponse(text, "end_turn", "claude-haiku-5-5", 600, 130, 0, 0);
		};
	}

}
