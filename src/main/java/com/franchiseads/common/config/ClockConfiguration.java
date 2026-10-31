package com.franchiseads.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One {@link Clock} for everything that measures deadlines, so tests can substitute a fixed one. */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
