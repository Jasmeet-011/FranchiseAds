package com.franchiseads;

import static org.assertj.core.api.Assertions.assertThat;

import com.franchiseads.brand.Brand;
import com.franchiseads.brand.BrandRepository;
import java.util.List;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The first test leaves rows behind on purpose; the second proves the extension removed them, restarted the identity
 * sequences and kept Flyway's history. The order is fixed so the second test always runs after the first.
 */
@Import(TestcontainersConfiguration.class)
@ExtendWith(TruncateTablesExtension.class)
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TruncateTablesExtensionTests {

	@Autowired
	private BrandRepository brands;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	@Order(1)
	void leavesRowsBehind() {
		brands.save(new Brand("Left Behind 1", null, List.of()));
		brands.save(new Brand("Left Behind 2", null, List.of()));

		assertThat(brands.count()).isEqualTo(2);
	}

	@Test
	@Order(2)
	void startsWithEmptyTablesRestartedIdentitiesAndFlywayHistoryKept() {
		assertThat(brands.count()).isZero();
		assertThat(brands.save(new Brand("First After Truncate", null, List.of())).getId()).isEqualTo(1L);
		assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history", Integer.class)).isPositive();
	}

}
