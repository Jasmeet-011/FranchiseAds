package com.franchiseads.common.openapi;

import com.franchiseads.variant.VariantAction;
import com.franchiseads.variant.VariantStatus;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

/**
 * OpenAPI document for springdoc (ADR-036). Error responses are declared per endpoint with {@link ProblemResponses}
 * and point at one shared response per status, all with the same {@code ProblemDetail} schema. springdoc's own
 * "copy every {@code @ExceptionHandler} onto every operation" is switched off in application.yml.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

	static final String PROBLEM_JSON = "application/problem+json";

	static final String PROBLEM_DETAIL_SCHEMA = "ProblemDetail";

	/** One shared response per status, worded after the error table in docs/flow.md. */
	static final Map<HttpStatus, String> PROBLEM_DESCRIPTIONS = new EnumMap<>(Map.of(
			HttpStatus.BAD_REQUEST,
			"Invalid request: a field failed validation (listed in `errors`), the JSON body is missing or unreadable, "
					+ "a path ID isn't a number, or `status` isn't a known value.",
			HttpStatus.NOT_FOUND, "The brand, location, campaign or variant doesn't exist.",
			HttpStatus.CONFLICT,
			"A brand with this name already exists (ignoring case); or the variant's status doesn't allow this "
					+ "action (`currentStatus`, `attemptedAction`); or the variant was modified concurrently.",
			HttpStatus.UNPROCESSABLE_CONTENT, "The location doesn't belong to the campaign's brand.",
			HttpStatus.BAD_GATEWAY,
			"No usable ad copy: the model's output was malformed on both attempts, or the AI provider rejected "
					+ "the request (for example a missing or invalid API key). Nothing was saved.",
			HttpStatus.SERVICE_UNAVAILABLE,
			"The AI provider was unavailable (429 or 5xx) after all retries, and no call produced usable copy. "
					+ "Nothing was saved.",
			HttpStatus.GATEWAY_TIMEOUT,
			"The AI provider timed out after all retries, or the 50s request deadline ran out, and no call "
					+ "produced usable copy. Nothing was saved."));

	@Bean
	OpenAPI franchiseAdsOpenApi() {
		return new OpenAPI().info(new Info().title("FranchiseAds API")
			.version("v1")
			.description("Localized Google Ads copy for every location of a franchise brand, generated with Claude, "
					+ "checked by a deterministic validator, and routed through an approval workflow. "
					+ "Errors are RFC 9457 problem details."));
	}

	@Bean
	OpenApiCustomizer problemDetailComponents() {
		return openApi -> {
			if (openApi.getComponents() == null) {
				openApi.setComponents(new Components());
			}
			Components components = openApi.getComponents();
			components.addSchemas(PROBLEM_DETAIL_SCHEMA, problemDetailSchema());
			Schema<?> ref = new Schema<>().$ref("#/components/schemas/" + PROBLEM_DETAIL_SCHEMA);
			PROBLEM_DESCRIPTIONS.forEach((status, description) -> components.addResponses(responseName(status),
					new ApiResponse().description(description)
						.content(new Content().addMediaType(PROBLEM_JSON, new MediaType().schema(ref)))));
		};
	}

	@Bean
	OperationCustomizer problemResponses() {
		return (operation, handlerMethod) -> {
			ProblemResponses problems = handlerMethod.getMethodAnnotation(ProblemResponses.class);
			if (problems != null) {
				for (HttpStatus status : problems.value()) {
					if (!PROBLEM_DESCRIPTIONS.containsKey(status)) {
						throw new IllegalStateException("No shared problem response for " + status + " on "
								+ handlerMethod.getShortLogMessage());
					}
					operation.getResponses()
						.addApiResponse(String.valueOf(status.value()),
								new ApiResponse().$ref("#/components/responses/" + responseName(status)));
				}
			}
			return operation;
		};
	}

	static String responseName(HttpStatus status) {
		return "Problem" + status.value();
	}

	private static Schema<?> problemDetailSchema() {
		ObjectSchema fieldError = new ObjectSchema();
		fieldError.addProperty("field", new StringSchema().example("bannedWords[1]"));
		fieldError.addProperty("message", new StringSchema().example("must not be blank"));

		ObjectSchema schema = new ObjectSchema();
		schema.description("RFC 9457 problem details. The extension properties appear only where noted.");
		schema.addProperty("type", new StringSchema().format("uri").example("about:blank"));
		schema.addProperty("title", new StringSchema().example("Conflict"));
		schema.addProperty("status", new IntegerSchema().example(409));
		schema.addProperty("detail", new StringSchema()
			.example("Can't approve a variant in status DRAFT: approve is allowed only from PENDING_REVIEW."));
		schema.addProperty("instance", new StringSchema().format("uri-reference").example("/variants/7/approve"));
		schema.addProperty("errors",
				new ArraySchema().items(fieldError).description("400 validation failures only, sorted by field."));
		schema.addProperty("currentStatus", enumSchema(VariantStatus.values())
			.description("409 from a refused review action only: the variant's status."));
		schema.addProperty("attemptedAction", enumSchema(VariantAction.values())
			.description("409 from a refused review action only: the action that was refused."));
		return schema;
	}

	private static StringSchema enumSchema(Enum<?>[] values) {
		StringSchema schema = new StringSchema();
		schema.setEnum(Arrays.stream(values).map(Enum::name).toList());
		return schema;
	}

}
