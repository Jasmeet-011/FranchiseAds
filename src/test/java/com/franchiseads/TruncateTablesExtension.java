package com.franchiseads;

import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Empties every application table before each test, so tests that write through a full Spring context can't see each
 * other's rows (ADR-031). Tables are discovered from the schema, so one added by a later migration is covered too;
 * {@code flyway_schema_history} is kept. Runs before {@code @BeforeEach} methods.
 */
public class TruncateTablesExtension implements BeforeEachCallback {

	private static final String APPLICATION_TABLES = """
			select quote_ident(tablename) from pg_tables
			where schemaname = current_schema() and tablename <> 'flyway_schema_history'
			order by tablename""";

	@Override
	public void beforeEach(ExtensionContext context) {
		DataSource dataSource = SpringExtension.getApplicationContext(context).getBean(DataSource.class);
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		List<String> tables = jdbc.queryForList(APPLICATION_TABLES, String.class);
		if (!tables.isEmpty()) {
			jdbc.execute("truncate table " + String.join(", ", tables) + " restart identity cascade");
		}
	}

}
