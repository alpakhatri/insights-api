package com.retailmedia.insights.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailmedia.insights.web.ProblemResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs right after JWT verification. Turns the verified token into a {@link Caller}, rejects
 * tokens without a tenant, and adds the tenant to every log line for this request.
 *
 * <p>Created in {@code SecurityConfig}, not as a bean, so it runs once inside the security chain.
 */
public class TenantContextFilter extends OncePerRequestFilter {

    static final String MDC_TENANT = "tenant_id";

    private final ObjectMapper mapper;

    public TenantContextFilter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            // Unauthenticated: public endpoints, or Spring Security returns 401 later in the chain.
            chain.doFilter(request, response);
            return;
        }

        Optional<Caller> caller = Caller.fromJwt(jwtAuth.getToken());
        if (caller.isEmpty()) {
            ProblemResponses.write(request, response, mapper, HttpStatus.FORBIDDEN,
                    "Token must carry tenant_id and a valid role (and brand_id for brand users)");
            return;
        }

        request.setAttribute(Caller.REQUEST_ATTRIBUTE, caller.get());
        MDC.put(MDC_TENANT, caller.get().tenantId());
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TENANT);
        }
    }
}
