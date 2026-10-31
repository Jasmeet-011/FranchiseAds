package com.franchiseads.common.error;

import com.franchiseads.claude.ClaudeRequestException;
import com.franchiseads.claude.ClaudeTimeoutException;
import com.franchiseads.claude.ClaudeUnavailableException;
import com.franchiseads.generation.LocationBrandMismatchException;
import com.franchiseads.generation.MalformedAdCopyException;
import com.franchiseads.variant.IllegalTransitionException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into an RFC 9457 {@link ProblemDetail}. Framework errors (unreadable body, wrong method,
 * missing parameter, ...) are handled by {@link ResponseEntityExceptionHandler}; status codes follow
 * docs/flow.md. Internal details never reach the client: upstream (Claude) failures get fixed details, and their
 * messages, which can quote Claude's error body, only go to the log.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** Unique constraints or indexes that mean "this already exists", with the detail returned for each. */
	private static final Map<String, String> UNIQUE_CONSTRAINT_DETAILS = Map.of("uq_brand_name_lower",
			"A brand with this name already exists (names are compared ignoring case).");

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Stream<FieldViolation> fieldErrors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()));
		Stream<FieldViolation> objectErrors = ex.getBindingResult()
			.getGlobalErrors()
			.stream()
			.map(error -> new FieldViolation(error.getObjectName(), error.getDefaultMessage()));
		List<FieldViolation> errors = Stream.concat(fieldErrors, objectErrors)
			.sorted(Comparator.comparing(FieldViolation::field)
				.thenComparing(FieldViolation::message, Comparator.nullsFirst(Comparator.naturalOrder())))
			.toList();

		ProblemDetail problem = ex.getBody();
		problem.setDetail("Request validation failed.");
		problem.setProperty("errors", errors);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	ProblemDetail handleNotFound(ResourceNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(LocationBrandMismatchException.class)
	ProblemDetail handleLocationBrandMismatch(LocationBrandMismatchException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
	}

	/** A review action the variant's status doesn't allow, or a submit of a flagged variant (ADR-032). */
	@ExceptionHandler(IllegalTransitionException.class)
	ProblemDetail handleIllegalTransition(IllegalTransitionException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
		problem.setProperty("currentStatus", ex.getCurrentStatus());
		problem.setProperty("attemptedAction", ex.getAttemptedAction());
		return problem;
	}

	/** Reaches here only when no attempt produced parseable copy (ADR-030). */
	@ExceptionHandler(MalformedAdCopyException.class)
	ProblemDetail handleMalformedAdCopy(MalformedAdCopyException ex) {
		log.warn("Generation failed: {}", ex.getMessage());
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
				"The AI model did not return usable ad copy. Try again.");
	}

	/** Our request was rejected (bad key, invalid request) or no key is configured: a bug or misconfiguration. */
	@ExceptionHandler(ClaudeRequestException.class)
	ProblemDetail handleClaudeRequest(ClaudeRequestException ex) {
		log.error("Claude rejected the request", ex);
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "The AI provider rejected the request.");
	}

	@ExceptionHandler(ClaudeUnavailableException.class)
	ProblemDetail handleClaudeUnavailable(ClaudeUnavailableException ex) {
		log.warn("Generation failed: {}", ex.getMessage());
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"The AI provider is unavailable. Try again later.");
	}

	@ExceptionHandler(ClaudeTimeoutException.class)
	ProblemDetail handleClaudeTimeout(ClaudeTimeoutException ex) {
		log.warn("Generation failed: {}", ex.getMessage());
		return ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT,
				"The AI provider did not respond in time. Try again later.");
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
		String detail = uniqueViolationDetail(ex);
		if (detail != null) {
			return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
		}
		// Request validation should prevent every other violation, so reaching this is a bug.
		log.error("Unmapped data integrity violation", ex);
		return internalServerError();
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return internalServerError();
	}

	private static String uniqueViolationDetail(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation
					&& violation.getKind() == ConstraintKind.UNIQUE) {
				return UNIQUE_CONSTRAINT_DETAILS.get(violation.getConstraintName());
			}
		}
		return null;
	}

	private static ProblemDetail internalServerError() {
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
	}

	/** One failed field in a 400 response: {@code {"field": "bannedWords[2]", "message": "must not be blank"}}. */
	public record FieldViolation(String field, String message) {
	}

}
