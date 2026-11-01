package com.franchiseads.common.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Cross-origin access for a browser UI (ADR-037).
 *
 * @param allowedOrigins exact origins allowed to call the API from a browser, e.g. a local Vite dev server. Both
 * localhost forms are in the default because browsers treat them as different origins.
 */
@Validated
@ConfigurationProperties("franchiseads.cors")
public record CorsProperties(
		@DefaultValue({ "http://localhost:5173", "http://127.0.0.1:5173" }) @NotEmpty List<@NotBlank String> allowedOrigins) {
}
