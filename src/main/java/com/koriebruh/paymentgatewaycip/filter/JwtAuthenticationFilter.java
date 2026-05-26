package com.koriebruh.paymentgatewaycip.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SCOPE_CLAIM   = "scope";
    private static final String ROLES_CLAIM   = "roles";

    private final JwtDecoder jwtDecoder;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawToken = authHeader.substring(BEARER_PREFIX.length()).strip();

        try {
            Jwt jwt = jwtDecoder.decode(rawToken);
            validateExpiry(jwt);

            List<SimpleGrantedAuthority> authorities = extractAuthorities(jwt);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(jwt.getSubject(), null, authorities)
            );

            log.debug("JWT authenticated subject={} authorities={}", jwt.getSubject(), authorities);

        } catch (JwtException | IllegalStateException ex) {
            log.warn("JWT validation failed reason='{}' uri={}", ex.getMessage(), request.getRequestURI());
            writeUnauthorized(response, ex.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void validateExpiry(Jwt jwt) {
        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
            throw new JwtException("Token expired at " + expiresAt);
        }
    }

    private List<SimpleGrantedAuthority> extractAuthorities(Jwt jwt) {
        String scope = jwt.getClaimAsString(SCOPE_CLAIM);
        if (scope != null && !scope.isBlank()) {
            return List.of(scope.split(" ")).stream()
                    .map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                    .toList();
        }

        return Optional.ofNullable(jwt.getClaimAsStringList(ROLES_CLAIM))
                .orElse(Collections.emptyList())
                .stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
    }

    private void writeUnauthorized(HttpServletResponse response, String reason) throws IOException {
        String traceId = org.slf4j.MDC.get(TraceContextFilter.MDC_TRACE_ID_KEY);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("""
                {"timestamp":"%s","traceId":"%s","status":"UNAUTHORIZED","message":"JWT authentication failed: %s"}
                """.formatted(Instant.now(), traceId != null ? traceId : "n/a", reason));
    }
}
