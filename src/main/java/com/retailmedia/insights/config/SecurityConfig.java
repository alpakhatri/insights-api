package com.retailmedia.insights.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailmedia.insights.security.RateLimitFilter;
import com.retailmedia.insights.security.TenantContextFilter;
import com.retailmedia.insights.security.TenantRateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

/**
 * Stateless JWT security.
 *
 * <p>Filter order for an API request:
 * <ol>
 *   <li>{@code BearerTokenAuthenticationFilter}: verifies the JWT signature and expiry (401 if invalid)
 *   <li>{@link TenantContextFilter}: builds the caller from the claims (403 if no tenant)
 *   <li>{@link RateLimitFilter}: per-tenant rate limit (429 if exceeded)
 *   <li>Controller, where the campaign ownership check runs (404 if not theirs)
 * </ol>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
        "/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus"
    };

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http, InsightsProperties properties,
                                           ObjectMapper mapper, ObjectProvider<MeterRegistry> meterRegistry)
            throws Exception {
        TenantContextFilter tenantContext = new TenantContextFilter(mapper);
        // The registry is always present when the full app runs (actuator); the fallback keeps
        // lightweight web-slice tests working without metrics auto-configuration.
        RateLimitFilter rateLimit = new RateLimitFilter(
                new TenantRateLimiter(properties.rateLimit(), System::nanoTime), mapper,
                meterRegistry.getIfAvailable(SimpleMeterRegistry::new));

        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                .addFilterAfter(tenantContext, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(rateLimit, TenantContextFilter.class);
        return http.build();
    }

    /**
     * Production: verify tokens against the identity provider's published keys ({@code issuer-uri}).
     * Local and tests: a shared HMAC secret, so tokens can be minted without an identity provider.
     */
    @Bean
    public JwtDecoder jwtDecoder(InsightsProperties properties) {
        InsightsProperties.Jwt jwt = properties.security().jwt();
        if (StringUtils.hasText(jwt.issuerUri())) {
            return JwtDecoders.fromIssuerLocation(jwt.issuerUri());
        }
        if (StringUtils.hasText(jwt.hmacSecret())) {
            byte[] secret = jwt.hmacSecret().getBytes(StandardCharsets.UTF_8);
            if (secret.length < 32) {
                throw new IllegalStateException("insights.security.jwt.hmac-secret must be at least 32 bytes");
            }
            return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret, "HmacSHA256"))
                    .macAlgorithm(MacAlgorithm.HS256)
                    .build();
        }
        throw new IllegalStateException(
                "Configure insights.security.jwt.issuer-uri (production) or hmac-secret (local)");
    }
}
