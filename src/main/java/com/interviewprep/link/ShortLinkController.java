package com.interviewprep.link;

import com.interviewprep.link.dto.CreateShortLinkRequest;
import com.interviewprep.link.dto.ShortLinkResponse;
import com.interviewprep.link.dto.ShortLinkStatsResponse;
import com.interviewprep.link.dto.ShortenResult;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/links")
public class ShortLinkController {

    private final ShortLinkService shortLinkService;

    public ShortLinkController(ShortLinkService shortLinkService) {
        this.shortLinkService = shortLinkService;
    }

    /**
     * 201 + {@code Location} (the new link's stats URL) when a link is created; 200 with the existing link when the same
     * URL and expiry were shortened before.
     */
    @PostMapping
    public ResponseEntity<ShortLinkResponse> create(@Valid @RequestBody CreateShortLinkRequest request) {
        ShortenResult result = shortLinkService.shorten(request);
        if (!result.created()) {
            return ResponseEntity.ok(result.link());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{code}/stats")
                .buildAndExpand(result.link().code())
                .toUri();
        return ResponseEntity.created(location).body(result.link());
    }

    @GetMapping("/{code}/stats")
    public ShortLinkStatsResponse stats(@PathVariable String code) {
        return shortLinkService.stats(code);
    }
}
