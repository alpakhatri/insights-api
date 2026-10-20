package com.retailmedia.insights.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailmedia.insights.web.ProblemResponses;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies the per-tenant rate limit. Returns 429 with a Retry-After header when a tenant has used
 * its budget, and counts rejections so they show up on dashboards.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final TenantRateLimiter limiter;
    private final ObjectMapper mapper;
    private final MeterRegistry meterRegistry;

    public RateLimitFilter(TenantRateLimiter limiter, ObjectMapper mapper, MeterRegistry meterRegistry) {
        this.limiter = limiter;
        this.mapper = mapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Caller caller = (Caller) request.getAttribute(Caller.REQUEST_ATTRIBUTE);
        if (caller == null) {
            chain.doFilter(request, response);
            return;
        }

        TokenBucket bucket = limiter.bucketFor(caller.tenantId());
        if (bucket.tryConsume()) {
            chain.doFilter(request, response);
            return;
        }

        meterRegistry.counter("insights.ratelimit.rejected", "tenant", caller.tenantId()).increment();
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(bucket.secondsUntilNextToken()));
        ProblemResponses.write(request, response, mapper, HttpStatus.TOO_MANY_REQUESTS,
                "Request rate limit exceeded for this tenant");
    }
}
