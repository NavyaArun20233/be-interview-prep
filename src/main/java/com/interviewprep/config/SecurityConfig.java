package com.interviewprep.config;

import com.interviewprep.exception.ProblemDetailsSecurityHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stateless bearer-token security, deny by default: every request needs a valid access token unless it is listed as
 * public below; {@code /api/v1/users} and product writes (PUT/DELETE) additionally need the ADMIN role. 401 and 403 are Problem Details JSON.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Same pattern as the short-link redirect route; any other root path still requires a token. */
    private static final String SHORT_LINK_REDIRECT = "/{code:[A-Za-z0-9]{1,8}}";

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ProblemDetailsSecurityHandler problemDetailsSecurityHandler,
            JwtAuthenticationConverter jwtAuthenticationConverter) {
        http
                // Tokens travel in the Authorization header, never in cookies, so CSRF does not apply.
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, SHORT_LINK_REDIRECT)
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health/**")
                        .permitAll()
                        .requestMatchers("/error")
                        .permitAll()
                        .requestMatchers("/api/v1/users")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/products/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/products/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        // Spring Security serves RFC 9728 metadata at /.well-known/oauth-protected-resource;
                        // its default claims certificate-bound tokens, which this service does not use.
                        .protectedResourceMetadata(metadata -> metadata.protectedResourceMetadataCustomizer(
                                builder -> builder.tlsClientCertificateBoundAccessTokens(false)))
                        .authenticationEntryPoint(problemDetailsSecurityHandler)
                        .accessDeniedHandler(problemDetailsSecurityHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemDetailsSecurityHandler)
                        .accessDeniedHandler(problemDetailsSecurityHandler));
        return http.build();
    }

    /** Maps the token's {@code roles} claim (e.g. {@code ["ADMIN"]}) to authorities such as {@code ROLE_ADMIN}. */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtConfig.ROLES_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    ProblemDetailsSecurityHandler problemDetailsSecurityHandler(JsonMapper jsonMapper) {
        return new ProblemDetailsSecurityHandler(jsonMapper);
    }

    /** bcrypt, stored with an {@code {bcrypt}} prefix so the algorithm can be upgraded later without a migration. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
