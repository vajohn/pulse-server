package com.edge.pulse.controllers;

import com.edge.pulse.configs.TranslationProperties;
import com.edge.pulse.data.dto.TranslateBatchRequest;
import com.edge.pulse.data.dto.TranslateBatchResponse;
import com.edge.pulse.data.dto.TranslateRequest;
import com.edge.pulse.data.dto.TranslateResponse;
import com.edge.pulse.services.AuditService;
import com.edge.pulse.services.translation.CachingTranslationService;
import com.edge.pulse.services.translation.TranslationRateGuard;
import com.edge.pulse.util.AppLocale;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Admin translation endpoints — proxies text through the configured
 * {@link TranslationService} implementation.
 *
 * <p>Rate limiting is applied at two independent layers:
 * <ol>
 *   <li>Request frequency — the existing Bucket4j filter covers all {@code /api/**} routes
 *       including these endpoints, keyed by JWT {@code sub}. No additional config needed.
 *   <li>Daily character budget — {@link TranslationRateGuard} tracks cumulative characters
 *       translated per user per day in Redis. Returns HTTP 429 when the budget is exceeded.
 * </ol>
 */
@RestController
@RequestMapping("/api/admin/translate")
@RequiredArgsConstructor
public class AdminTranslationController {

    private final CachingTranslationService translationService;
    private final TranslationRateGuard rateGuard;
    private final AuditService auditService;
    private final TranslationProperties translationProperties;

    /**
     * Translate a single text.
     *
     * @return 200 with translated text, or 400/429 on validation / budget failure.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('FORM_CREATE') or hasAuthority('ASSESS_CREATE')")
    public ResponseEntity<?> translate(@RequestBody @Valid TranslateRequest request,
                                       Authentication auth) {
        if (!AppLocale.isSupported(request.fromLocale()) ||
            !AppLocale.isSupported(request.toLocale())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "unsupported_locale",
                                 "supported", AppLocale.ALL));
        }

        UUID userId = (UUID) auth.getPrincipal();
        int totalChars = request.text().length();
        if (!rateGuard.tryConsume(userId, totalChars)) {
            return ResponseEntity.status(429)
                    .body(Map.of("error", "daily_char_budget_exceeded",
                                 "budget", translationProperties.getDailyCharBudget()));
        }

        CachingTranslationService.TranslationResult result =
                translationService.translateSingle(
                        request.text(), request.fromLocale(), request.toLocale());

        auditService.logAction(userId, "TRANSLATE_SINGLE", "TRANSLATION", null,
                auditService.buildDetail(
                        "toLocale", request.toLocale(),
                        "charCount", totalChars,
                        "provider", translationService.getProvider().name()),
                null);

        return ResponseEntity.ok(new TranslateResponse(
                result.text(),
                translationService.getProvider().name(),
                result.cached()));
    }

    /**
     * Translate multiple texts in a single provider call (up to 50 items).
     * All strings in the admin dialog are bundled into one request to minimise round-trips.
     *
     * @return 200 with translated texts list, or 400/429 on validation / budget failure.
     */
    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('FORM_CREATE') or hasAuthority('ASSESS_CREATE')")
    public ResponseEntity<?> translateBatch(@RequestBody @Valid TranslateBatchRequest request,
                                             Authentication auth) {
        if (!AppLocale.isSupported(request.fromLocale()) ||
            !AppLocale.isSupported(request.toLocale())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "unsupported_locale",
                                 "supported", AppLocale.ALL));
        }

        UUID userId = (UUID) auth.getPrincipal();
        int totalChars = request.texts().stream().mapToInt(String::length).sum();
        if (!rateGuard.tryConsume(userId, totalChars)) {
            return ResponseEntity.status(429)
                    .body(Map.of("error", "daily_char_budget_exceeded",
                                 "budget", translationProperties.getDailyCharBudget()));
        }

        var translated = translationService.translateBatch(
                request.texts(), request.fromLocale(), request.toLocale());

        auditService.logAction(userId, "TRANSLATE_BATCH", "TRANSLATION", null,
                auditService.buildDetail(
                        "toLocale", request.toLocale(),
                        "itemCount", request.texts().size(),
                        "charCount", totalChars,
                        "provider", translationService.getProvider().name()),
                null);

        return ResponseEntity.ok(new TranslateBatchResponse(
                translated,
                translationService.getProvider().name()));
    }
}
