package com.interviewprep;

import com.interviewprep.config.SecurityConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * The production security rules for {@code @WebMvcTest} slices, without the signing key. Slices authenticate with
 * {@code SecurityMockMvcRequestPostProcessors.jwt()} or {@code @WithMockUser}; a raw bearer token that reaches this
 * decoder is always rejected.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(SecurityConfig.class)
public class WebMvcSecurityTestConfiguration {

    @Bean
    JwtDecoder jwtDecoder() {
        return token -> {
            throw new BadJwtException("Slice tests do not decode real tokens");
        };
    }
}
