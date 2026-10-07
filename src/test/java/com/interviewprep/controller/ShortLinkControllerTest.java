package com.interviewprep.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.interviewprep.config.ValidationConfig;
import com.interviewprep.dto.link.CreateShortLinkRequest;
import com.interviewprep.dto.link.ShortLinkResponse;
import com.interviewprep.dto.link.ShortLinkStatsResponse;
import com.interviewprep.dto.link.ShortenResult;
import com.interviewprep.exception.FieldValidationException;
import com.interviewprep.exception.ShortLinkExpiredException;
import com.interviewprep.exception.ShortLinkNotFoundException;
import com.interviewprep.service.ShortLinkService;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({ShortLinkController.class, ShortLinkRedirectController.class})
@Import({ValidationConfig.class, ShortLinkControllerTest.FixedClockConfig.class})
class ShortLinkControllerTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final String URL = "https://example.com/a/very/long/path";

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShortLinkService shortLinkService;

    private static ShortLinkResponse link(String code, Instant expiresAt) {
        return new ShortLinkResponse(code, "http://sho.rt/" + code, URL, NOW, expiresAt);
    }

    @Test
    void createReturns201WithStatsLocationForNewLink() throws Exception {
        Instant expiry = Instant.parse("2026-12-31T18:29:59Z");
        when(shortLinkService.shorten(any(CreateShortLinkRequest.class)))
                .thenReturn(new ShortenResult(link("abc1234", expiry), true));

        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "  %s  ", "expiresAt": "2026-12-31T23:59:59+05:30"}
                                """.formatted(URL)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/links/abc1234/stats"))
                .andExpect(jsonPath("$.code").value("abc1234"))
                .andExpect(jsonPath("$.shortUrl").value("http://sho.rt/abc1234"))
                .andExpect(jsonPath("$.originalUrl").value(URL))
                .andExpect(jsonPath("$.createdAt").value("2026-01-15T10:00:00Z"))
                .andExpect(jsonPath("$.expiresAt").value("2026-12-31T18:29:59Z"));

        // The URL is stripped of surrounding whitespace; Jackson normalizes the offset (the instant is unchanged).
        verify(shortLinkService).shorten(new CreateShortLinkRequest(URL, OffsetDateTime.parse("2026-12-31T18:29:59Z")));
    }

    @Test
    void createReturns200WithoutLocationForExistingLink() throws Exception {
        when(shortLinkService.shorten(any(CreateShortLinkRequest.class)))
                .thenReturn(new ShortenResult(link("abc1234", null), false));

        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"" + URL + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.code").value("abc1234"))
                .andExpect(jsonPath("$.expiresAt").doesNotExist());
    }

    @Test
    void createReportsInvalidUrlAndPastExpiryTogether() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "javascript:alert(1)", "expiresAt": "2026-01-15T10:00:00Z"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[?(@.field == 'url')].message").value("must be a valid http or https URL"))
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'expiresAt')].message").value("must be a future date"));

        verifyNoInteractions(shortLinkService);
    }

    @Test
    void createRejectsMissingOrBlankUrl() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("url"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"));

        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("url"));

        verifyNoInteractions(shortLinkService);
    }

    @Test
    void createRejectsUrlLongerThan2048Characters() throws Exception {
        String url = "https://example.com/" + "a".repeat(2049 - "https://example.com/".length());

        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"" + url + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("url"))
                .andExpect(jsonPath("$.errors[0].message").value("size must be between 0 and 2048"));

        verifyNoInteractions(shortLinkService);
    }

    @Test
    void createRejectsMalformedExpiry() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "%s", "expiresAt": "next tuesday"}
                                """.formatted(URL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("expiresAt"));

        verifyNoInteractions(shortLinkService);
    }

    @Test
    void createMapsServiceFieldValidationTo400() throws Exception {
        when(shortLinkService.shorten(any(CreateShortLinkRequest.class)))
                .thenThrow(new FieldValidationException("expiresAt", "must be a future date"));

        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "%s", "expiresAt": "2026-01-15T10:00:01Z"}
                                """.formatted(URL)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0].field").value("expiresAt"))
                .andExpect(jsonPath("$.errors[0].message").value("must be a future date"));
    }

    @Test
    void redirectReturns302WithLocationAndNoStore() throws Exception {
        when(shortLinkService.visit("abc1234")).thenReturn(URL);

        mockMvc.perform(get("/abc1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", URL))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void redirectReturns404ProblemDetailForUnknownCode() throws Exception {
        when(shortLinkService.visit("nope123")).thenThrow(new ShortLinkNotFoundException("nope123"));

        mockMvc.perform(get("/nope123"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Short link nope123 not found"))
                .andExpect(jsonPath("$.instance").value("/nope123"));
    }

    @Test
    void redirectReturns410ProblemDetailForExpiredCode() throws Exception {
        when(shortLinkService.visit("old1234")).thenThrow(new ShortLinkExpiredException("old1234"));

        mockMvc.perform(get("/old1234"))
                .andExpect(status().isGone())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Gone"))
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.detail").value("Short link old1234 has expired"))
                .andExpect(jsonPath("$.instance").value("/old1234"));
    }

    @Test
    void redirectPatternDoesNotMatchCodesOutsideAlphabetOrLength() throws Exception {
        mockMvc.perform(get("/abc123456")).andExpect(status().isNotFound());
        mockMvc.perform(get("/abc-123")).andExpect(status().isNotFound());
        mockMvc.perform(get("/favicon.ico")).andExpect(status().isNotFound());

        verifyNoInteractions(shortLinkService);
    }

    @Test
    void statsReturnsCountsAndExpiry() throws Exception {
        when(shortLinkService.stats("abc1234"))
                .thenReturn(new ShortLinkStatsResponse(
                        "abc1234", "http://sho.rt/abc1234", URL, 42, NOW, NOW.plusSeconds(60), false));

        mockMvc.perform(get("/api/v1/links/abc1234/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("abc1234"))
                .andExpect(jsonPath("$.shortUrl").value("http://sho.rt/abc1234"))
                .andExpect(jsonPath("$.originalUrl").value(URL))
                .andExpect(jsonPath("$.visitCount").value(42))
                .andExpect(jsonPath("$.createdAt").value("2026-01-15T10:00:00Z"))
                .andExpect(jsonPath("$.expiresAt").value("2026-01-15T10:01:00Z"))
                .andExpect(jsonPath("$.expired").value(false));
    }

    @Test
    void statsReturns404ForUnknownCode() throws Exception {
        when(shortLinkService.stats("nope123")).thenThrow(new ShortLinkNotFoundException("nope123"));

        mockMvc.perform(get("/api/v1/links/nope123/stats"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Short link nope123 not found"));
    }
}
