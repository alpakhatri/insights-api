package com.retailmedia.insights.security;

import java.util.Objects;
import java.util.Optional;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The authenticated caller, built only from a verified JWT. The tenant is never taken from the
 * request path or query, so a caller cannot ask for another tenant's data.
 *
 * <p>Expected claims: {@code sub}, {@code tenant_id}, {@code role} ({@code RETAILER} or
 * {@code BRAND}) and, for brand users, {@code brand_id}.
 */
public record Caller(String subject, String tenantId, Role role, String brandId) {

    /** Request attribute under which the resolved caller is stored for the rest of the request. */
    public static final String REQUEST_ATTRIBUTE = Caller.class.getName();

    public Caller {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(role, "role");
        if (role == Role.BRAND && (brandId == null || brandId.isBlank())) {
            throw new IllegalArgumentException("A brand caller needs a brand_id");
        }
    }

    /** Builds a caller from token claims, or empty if the token lacks what this API needs. */
    public static Optional<Caller> fromJwt(Jwt jwt) {
        String tenantId = jwt.getClaimAsString("tenant_id");
        String roleClaim = jwt.getClaimAsString("role");
        String brandId = jwt.getClaimAsString("brand_id");
        if (tenantId == null || tenantId.isBlank() || roleClaim == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Caller(jwt.getSubject(), tenantId, Role.valueOf(roleClaim), brandId));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
