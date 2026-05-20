package com.stockforecast.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Assigns every request a correlation id (accepting a well-formed inbound {@value #HEADER_NAME} header),
 * exposes it in the logging MDC and echoes it in the response. Runs before Spring Security so that
 * authentication failures are correlated too, and also on error dispatches.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final String REQUEST_ATTRIBUTE = CorrelationIdFilter.class.getName() + ".id";
    /** Restrictive on purpose: inbound ids end up in logs, so reject anything that could forge log lines. */
    private static final Pattern ACCEPTED_FORMAT = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    /**
     * Returns the correlation id of the given request, or {@code null} if the filter has not processed it.
     */
    public static String currentCorrelationId(HttpServletRequest request) {
        Object id = request.getAttribute(REQUEST_ATTRIBUTE);
        return id instanceof String value ? value : MDC.get(MDC_KEY);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = resolve(request);
        request.setAttribute(REQUEST_ATTRIBUTE, correlationId);
        if (!response.containsHeader(HEADER_NAME)) {
            response.setHeader(HEADER_NAME, correlationId);
        }
        MDC.put(MDC_KEY, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response,
                                               FilterChain chain) throws ServletException, IOException {
        doFilterInternal(request, response, chain);
    }

    private static String resolve(HttpServletRequest request) {
        Object existing = request.getAttribute(REQUEST_ATTRIBUTE);
        if (existing instanceof String id) {
            return id;
        }
        String inbound = request.getHeader(HEADER_NAME);
        if (inbound != null && ACCEPTED_FORMAT.matcher(inbound).matches()) {
            return inbound;
        }
        return UUID.randomUUID().toString();
    }
}
