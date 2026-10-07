package com.interviewprep.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.interviewprep.common.error.FieldValidationException;
import com.interviewprep.link.dto.CreateShortLinkRequest;
import com.interviewprep.link.dto.ShortLinkStatsResponse;
import com.interviewprep.link.dto.ShortenResult;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShortLinkServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String URL = "https://example.com/some/long/path?q=1";
    private static final String URL_HASH = ShortLinkService.sha256Hex(URL);

    @Mock
    private ShortLinkRepository repository;

    @Mock
    private ShortCodeGenerator codeGenerator;

    private ShortLinkService service;

    @BeforeEach
    void setUp() {
        service = new ShortLinkService(repository, codeGenerator, new ShortLinkProperties("https://sho.rt/"), CLOCK);
    }

    @Test
    void shortenCreatesNewLinkWithGeneratedCode() {
        when(repository.findByUrlHashAndExpiresAtIsNull(URL_HASH)).thenReturn(Optional.empty());
        when(codeGenerator.generate()).thenReturn("abc1234");
        when(repository.insertIfAbsent(any(ShortLink.class))).thenReturn(1);

        ShortenResult result = service.shorten(new CreateShortLinkRequest(URL, null));

        assertThat(result.created()).isTrue();
        assertThat(result.link().code()).isEqualTo("abc1234");
        assertThat(result.link().shortUrl()).isEqualTo("https://sho.rt/abc1234");
        assertThat(result.link().originalUrl()).isEqualTo(URL);
        assertThat(result.link().createdAt()).isEqualTo(NOW);
        assertThat(result.link().expiresAt()).isNull();
        ArgumentCaptor<ShortLink> inserted = ArgumentCaptor.forClass(ShortLink.class);
        verify(repository).insertIfAbsent(inserted.capture());
        assertThat(inserted.getValue().getUrlHash()).isEqualTo(URL_HASH).hasSize(64);
    }

    @Test
    void shortenReturnsExistingLinkForSameUrlAndExpiry() {
        Instant expiry = NOW.plusSeconds(3600);
        ShortLink existing = new ShortLink("old0001", URL, URL_HASH, expiry, NOW.minusSeconds(60));
        when(repository.findByUrlHashAndExpiresAt(URL_HASH, expiry)).thenReturn(Optional.of(existing));

        ShortenResult result = service.shorten(
                new CreateShortLinkRequest(URL, OffsetDateTime.ofInstant(expiry, ZoneOffset.ofHours(5))));

        assertThat(result.created()).isFalse();
        assertThat(result.link().code()).isEqualTo("old0001");
        assertThat(result.link().expiresAt()).isEqualTo(expiry);
        verify(repository, never()).insertIfAbsent(any(ShortLink.class));
        verify(codeGenerator, never()).generate();
    }

    @Test
    void shortenReturnsLinkCreatedConcurrentlyWhenInsertConflictsOnUrl() {
        ShortLink concurrent = new ShortLink("win0001", URL, URL_HASH, null, NOW);
        when(repository.findByUrlHashAndExpiresAtIsNull(URL_HASH))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(concurrent));
        when(codeGenerator.generate()).thenReturn("los0001");
        when(repository.insertIfAbsent(any(ShortLink.class))).thenReturn(0);

        ShortenResult result = service.shorten(new CreateShortLinkRequest(URL, null));

        assertThat(result.created()).isFalse();
        assertThat(result.link().code()).isEqualTo("win0001");
        verify(repository, times(1)).insertIfAbsent(any(ShortLink.class));
    }

    @Test
    void shortenRetriesWithFreshCodeOnCodeCollision() {
        when(repository.findByUrlHashAndExpiresAtIsNull(URL_HASH)).thenReturn(Optional.empty());
        when(codeGenerator.generate()).thenReturn("taken01", "taken02", "free001");
        when(repository.insertIfAbsent(any(ShortLink.class))).thenReturn(0, 0, 1);

        ShortenResult result = service.shorten(new CreateShortLinkRequest(URL, null));

        assertThat(result.created()).isTrue();
        assertThat(result.link().code()).isEqualTo("free001");
        verify(repository, times(3)).insertIfAbsent(any(ShortLink.class));
    }

    @Test
    void shortenGivesUpAfterBoundedCodeCollisions() {
        when(repository.findByUrlHashAndExpiresAtIsNull(URL_HASH)).thenReturn(Optional.empty());
        when(codeGenerator.generate()).thenReturn("taken01");
        when(repository.insertIfAbsent(any(ShortLink.class))).thenReturn(0);

        assertThatThrownBy(() -> service.shorten(new CreateShortLinkRequest(URL, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unique short code");
        verify(repository, times(ShortLinkService.MAX_CODE_ATTEMPTS)).insertIfAbsent(any(ShortLink.class));
    }

    @Test
    void shortenRejectsExpiryThatIsNotInTheFuture() {
        OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);

        assertThatThrownBy(() -> service.shorten(new CreateShortLinkRequest(URL, now)))
                .isInstanceOfSatisfying(
                        FieldValidationException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("expiresAt"));
        verify(repository, never()).insertIfAbsent(any(ShortLink.class));
    }

    @Test
    void shortenTruncatesExpiryToMicrosecondsAndAcceptsAnyOffset() {
        OffsetDateTime expiry = OffsetDateTime.parse("2026-01-15T15:30:00.123456789+05:30");
        Instant stored = Instant.parse("2026-01-15T10:00:00.123456Z");
        when(repository.findByUrlHashAndExpiresAt(URL_HASH, stored)).thenReturn(Optional.empty());
        when(codeGenerator.generate()).thenReturn("abc1234");
        when(repository.insertIfAbsent(any(ShortLink.class))).thenReturn(1);

        ShortenResult result = service.shorten(new CreateShortLinkRequest(URL, expiry));

        assertThat(result.link().expiresAt()).isEqualTo(stored);
    }

    @Test
    void shortenRefusesExistingRowWithSameHashButDifferentUrl() {
        ShortLink clash = new ShortLink("cls0001", "https://other.example", URL_HASH, null, NOW);
        when(repository.findByUrlHashAndExpiresAtIsNull(URL_HASH)).thenReturn(Optional.of(clash));

        assertThatThrownBy(() -> service.shorten(new CreateShortLinkRequest(URL, null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void visitCountsAndReturnsOriginalUrlForActiveLink() {
        when(repository.incrementVisitCountIfActive("abc1234", NOW)).thenReturn(1);
        when(repository.findOriginalUrlByCode("abc1234")).thenReturn(Optional.of(URL));

        assertThat(service.visit("abc1234")).isEqualTo(URL);
    }

    @Test
    void visitThrowsNotFoundForUnknownCode() {
        when(repository.incrementVisitCountIfActive("nope123", NOW)).thenReturn(0);
        when(repository.existsByCode("nope123")).thenReturn(false);

        assertThatThrownBy(() -> service.visit("nope123"))
                .isInstanceOf(ShortLinkNotFoundException.class)
                .hasMessage("Short link nope123 not found");
    }

    @Test
    void visitThrowsExpiredForExistingCodeThatWasNotCounted() {
        when(repository.incrementVisitCountIfActive("old1234", NOW)).thenReturn(0);
        when(repository.existsByCode("old1234")).thenReturn(true);

        assertThatThrownBy(() -> service.visit("old1234"))
                .isInstanceOf(ShortLinkExpiredException.class)
                .hasMessage("Short link old1234 has expired");
        verify(repository, never()).findOriginalUrlByCode(any());
    }

    @Test
    void statsReportsCountsAndExpiryFlag() {
        ShortLink expired = new ShortLink("old1234", URL, URL_HASH, NOW, NOW.minusSeconds(3600));
        when(repository.findByCode("old1234")).thenReturn(Optional.of(expired));

        ShortLinkStatsResponse stats = service.stats("old1234");

        assertThat(stats.code()).isEqualTo("old1234");
        assertThat(stats.shortUrl()).isEqualTo("https://sho.rt/old1234");
        assertThat(stats.originalUrl()).isEqualTo(URL);
        assertThat(stats.visitCount()).isZero();
        assertThat(stats.createdAt()).isEqualTo(NOW.minusSeconds(3600));
        assertThat(stats.expiresAt()).isEqualTo(NOW);
        // Expiry is exclusive: a link expiring exactly "now" is no longer active.
        assertThat(stats.expired()).isTrue();
    }

    @Test
    void statsThrowsNotFoundForUnknownCode() {
        when(repository.findByCode("nope123")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.stats("nope123")).isInstanceOf(ShortLinkNotFoundException.class);
    }
}
