// Steps
// Log one line per request with the request ID, method, path, status and duration.
// Accept a safe X-Request-ID header or generate one, and return it on the response.
// Keep credentials, bodies and query strings out of the logs.
package com.orderflow.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// Similar to FastAPI middleware: it wraps every request, so it can time it and log the outcome.
// It runs before Spring Security so rejected (401) requests are logged as well.
// Only the path is logged, never the query string, headers or body.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String MDC_KEY = "request_id";

    // Short and free of spaces and control characters, so a client cannot inject odd text into logs.
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = effectiveRequestId(request.getHeader(REQUEST_ID_HEADER));
        response.setHeader(REQUEST_ID_HEADER, requestId);
        // MDC is thread-local context that the log pattern can print; Python's contextvars is the nearest idea.
        // Threads are reused between requests, so it must always be cleared.
        MDC.put(MDC_KEY, requestId);

        long start = System.nanoTime();
        int status = 500; // stays 500 if an unhandled exception escapes the chain
        try {
            chain.doFilter(request, response);
            status = response.getStatus();
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            log.info("request_id={} method={} path={} status={} duration_ms={}",
                    requestId, request.getMethod(), request.getRequestURI(), status, durationMs);
            MDC.remove(MDC_KEY);
        }
    }

    // Unsafe or oversized values are replaced rather than rejected, so a bad header never fails a request.
    private String effectiveRequestId(String supplied) {
        if (supplied != null && SAFE_REQUEST_ID.matcher(supplied).matches()) {
            return supplied;
        }
        return UUID.randomUUID().toString();
    }
}
