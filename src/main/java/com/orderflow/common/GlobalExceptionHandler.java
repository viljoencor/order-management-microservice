// Steps
// Return field-level details for request validation failures.
// Hide Jackson internals when the JSON cannot be read.
// Leave every other error to Spring's ProblemDetail handling, so business errors keep their details.
package com.orderflow.common;

import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// Similar to FastAPI exception handlers, but one class covers many exception types.
// The base class already turns the common Spring MVC exceptions, and the ResponseStatusException
// thrown by the services (with its missingProductIds property), into RFC 9457 ProblemDetail JSON,
// so only the two cases that need a different body are overridden here.
// Login failures never reach this class: Spring Security answers them with 401 before MVC runs.
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    public record FieldErrorDetail(String field, String message) {
    }

    // Similar to the 422 body FastAPI builds from a Pydantic ValidationError.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorDetail> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldErrorDetail::field).thenComparing(FieldErrorDetail::message))
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Request validation failed");
        problem.setProperty("fieldErrors", fieldErrors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    // The Jackson message can name internal Java types, so it is never sent to the client.
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Malformed or invalid JSON request");
        return handleExceptionInternal(ex, problem, headers, status, request);
    }
}
