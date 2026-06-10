package com.edge.pulse.services;

import com.edge.pulse.data.models.AnonIdentity;
import com.edge.pulse.data.models.Form;
import com.edge.pulse.repositories.AnonIdentityRepository;
import com.edge.pulse.repositories.FormRepository;
import com.edge.pulse.data.models.OrganizationalUnit;
import com.edge.pulse.repositories.OrganizationalUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AnonIdentityService {

    private final AnonIdentityRepository anonIdentityRepository;
    private final FormRepository formRepository;
    private final OrganizationalUnitRepository orgUnitRepository;
    private final FormCacheService cacheService;

    public AnonIdentity resolveOrCreate(UUID formId, UUID orgUnitId, String incomingToken, int windowMinutes) {
        // If a token was provided, try cache first (by hash), then DB
        if (incomingToken != null) {
            String incomingHash = sha256(incomingToken);
            Optional<AnonIdentity> cached = cacheService.get(
                    FormCacheService.anonTokenKey(incomingHash), AnonIdentity.class);
            if (cached.isPresent() && cached.get().getWindowEnd().isAfter(LocalDateTime.now())) {
                return cached.get();
            }

            Optional<AnonIdentity> existing = anonIdentityRepository.findByToken(incomingHash);
            if (existing.isPresent()) {
                AnonIdentity identity = existing.get();
                if (identity.getWindowEnd().isAfter(LocalDateTime.now())) {
                    cacheToRedis(identity, windowMinutes);
                    return identity;
                }
            }
        }

        // Calculate the current window using epoch minutes (not minute-of-hour)
        // so windows are globally consistent across hours and days.
        long epochMinute = Instant.now().getEpochSecond() / 60;
        long windowIndex = epochMinute / windowMinutes;
        long windowStartEpochSecond = windowIndex * windowMinutes * 60L;
        LocalDateTime windowStart = LocalDateTime.ofEpochSecond(windowStartEpochSecond, 0, ZoneOffset.UTC);
        LocalDateTime windowEnd = windowStart.plusMinutes(windowMinutes);

        Form form = formRepository.findById(formId)
                .orElseThrow(() -> new IllegalArgumentException("Form not found"));
        OrganizationalUnit orgUnit = orgUnitRepository.findById(orgUnitId)
                .orElseThrow(() -> new IllegalArgumentException("Org unit not found"));

        // SELECT FOR UPDATE to prevent race conditions
        List<AnonIdentity> existing = anonIdentityRepository.findLastInWindowForUpdate(orgUnitId, formId, windowStart);
        int nextSequence = existing.isEmpty() ? 1 : existing.getFirst().getSequenceInWindow() + 1;

        String rawToken = UUID.randomUUID().toString();
        String token = sha256(rawToken);
        AnonIdentity identity = AnonIdentity.builder()
                .orgUnit(orgUnit)
                .form(form)
                .token(token)
                .windowStart(windowStart)
                .windowEnd(windowEnd)
                .sequenceInWindow(nextSequence)
                .build();

        identity = anonIdentityRepository.save(identity);
        cacheToRedis(identity, windowMinutes);
        return identity;
    }

    @Transactional(readOnly = true)
    public Optional<AnonIdentity> resolveByToken(String token) {
        String tokenHash = sha256(token);

        // Try cache first (by hash)
        Optional<AnonIdentity> cached = cacheService.get(
                FormCacheService.anonTokenKey(tokenHash), AnonIdentity.class);
        if (cached.isPresent()) {
            return cached;
        }

        Optional<AnonIdentity> fromDb = anonIdentityRepository.findByToken(tokenHash);
        fromDb.ifPresent(identity -> {
            long remainingMinutes = Duration.between(LocalDateTime.now(), identity.getWindowEnd()).toMinutes();
            if (remainingMinutes > 0) {
                cacheService.put(FormCacheService.anonTokenKey(tokenHash),
                        identity, Duration.ofMinutes(remainingMinutes));
            }
        });
        return fromDb;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private void cacheToRedis(AnonIdentity identity, int windowMinutes) {
        cacheService.put(
                FormCacheService.anonTokenKey(identity.getToken()),
                identity,
                Duration.ofMinutes(windowMinutes));
    }
}
