package com.devilsvault.api.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ensures every request has a correlation ID — taken from the inbound
 * {@code X-Correlation-Id} header if present, otherwise generated. The ID
 * is placed in SLF4J's MDC so log lines and audit events carry it, and
 * echoed back in the response header for clients to surface in error UIs
 * (DORA Art. 17 forensics support, DORA-2.3).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_ACTOR = "actor";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String inbound = request.getHeader(HEADER);
        String correlationId = (inbound != null && !inbound.isBlank() && inbound.length() <= 64)
                ? inbound
                : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, correlationId);
        MDC.put(MDC_REQUEST_ID, correlationId);
        try {
            response.setHeader(HEADER, correlationId);
            log.debug("{} {}", request.getMethod(), request.getRequestURI());
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_ACTOR);
        }
    }

    /**
     * Sets the authenticated username into MDC so structured log events
     * carry the actor field. Called from the JWT authentication filter
     * after successful token validation.
     */
    public static void setActorFromPrincipal(Principal principal) {
        if (principal != null) {
            MDC.put(MDC_ACTOR, principal.getName());
        }
    }
}
