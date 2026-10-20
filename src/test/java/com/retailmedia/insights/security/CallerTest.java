package com.retailmedia.insights.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class CallerTest {

    @Test
    void buildsRetailerFromClaims() {
        Jwt jwt = jwt(Map.of("tenant_id", "tesco", "role", "RETAILER"));

        assertThat(Caller.fromJwt(jwt)).contains(new Caller("user-1", "tesco", Role.RETAILER, null));
    }

    @Test
    void buildsBrandFromClaims() {
        Jwt jwt = jwt(Map.of("tenant_id", "tesco", "role", "BRAND", "brand_id", "nestle"));

        assertThat(Caller.fromJwt(jwt)).contains(new Caller("user-1", "tesco", Role.BRAND, "nestle"));
    }

    @Test
    void rejectsTokenWithoutTenant() {
        assertThat(Caller.fromJwt(jwt(Map.of("role", "RETAILER")))).isEmpty();
    }

    @Test
    void rejectsUnknownRole() {
        assertThat(Caller.fromJwt(jwt(Map.of("tenant_id", "tesco", "role", "ADMIN")))).isEmpty();
    }

    @Test
    void rejectsBrandWithoutBrandId() {
        assertThat(Caller.fromJwt(jwt(Map.of("tenant_id", "tesco", "role", "BRAND")))).isEmpty();
    }

    private static Jwt jwt(Map<String, ?> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        claims.forEach(builder::claim);
        return builder.build();
    }
}
