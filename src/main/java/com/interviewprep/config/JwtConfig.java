package com.interviewprep.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Signs and verifies the application's own HS256 access tokens with the shared secret from {@link JwtProperties}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    public static final String ISSUER = "be-interview-prep";
    public static final String ROLES_CLAIM = "roles";
    public static final String EMAIL_CLAIM = "email";

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        return NimbusJwtEncoder.withSecretKey(properties.signingKey())
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * Accepts only HS256 tokens signed with our key, issued by us and carrying an {@code exp} claim. Clock skew is zero
     * (Spring's default is 60 s) so a token stops working exactly at {@code exp}: the issuer and the verifier are this
     * same service and the same {@link Clock}, so there is no skew to tolerate.
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties, Clock clock) {
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
        timestampValidator.setClock(clock);
        timestampValidator.setAllowEmptyExpiryClaim(false);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(properties.signingKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(
                JwtValidators.createDefaultWithValidators(timestampValidator, new JwtIssuerValidator(ISSUER)));
        return decoder;
    }
}
