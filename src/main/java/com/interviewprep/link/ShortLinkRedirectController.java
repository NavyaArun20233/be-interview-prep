package com.interviewprep.link;

import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves short URLs at the root ({@code /{code}}). The pattern only matches a single segment of the code alphabet and
 * length, so it cannot shadow {@code /api/**}, {@code /actuator/**} or any path containing a dot or slash.
 */
@RestController
public class ShortLinkRedirectController {

    private final ShortLinkService shortLinkService;

    public ShortLinkRedirectController(ShortLinkService shortLinkService) {
        this.shortLinkService = shortLinkService;
    }

    /**
     * 302, not 301: browsers cache a 301 permanently, so repeat visits would never reach the server to be counted and an
     * expired link would keep redirecting. {@code no-store} keeps other caches from replaying the redirect too.
     */
    @GetMapping("/{code:[A-Za-z0-9]{1,8}}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String originalUrl = shortLinkService.visit(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
