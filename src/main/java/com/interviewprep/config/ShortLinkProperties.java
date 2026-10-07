package com.interviewprep.config;

import com.interviewprep.validation.HttpUrl;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param baseUrl public origin that short codes are appended to ({@code app.links.base-url}, env
 *     {@code APP_LINKS_BASE_URL}); a trailing slash is ignored. Validated at startup so a blank or relative value fails
 *     fast instead of producing broken short URLs.
 */
@Validated
@ConfigurationProperties("app.links")
public record ShortLinkProperties(
        @DefaultValue("http://localhost:8080") @NotBlank @HttpUrl
        String baseUrl) {

    public ShortLinkProperties {
        while (baseUrl != null && baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
    }

    public String shortUrl(String code) {
        return baseUrl + "/" + code;
    }
}
