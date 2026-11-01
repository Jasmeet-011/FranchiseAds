package com.franchiseads.common.openapi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.http.HttpStatus;

/**
 * The {@code ProblemDetail} error responses an endpoint can return, per docs/flow.md. {@link OpenApiConfiguration}
 * turns each status into a reference to one shared response definition.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProblemResponses {

	HttpStatus[] value();

}
