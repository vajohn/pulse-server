package com.edge.pulse.services;

import com.edge.pulse.configs.CacheTtlProperties;
import com.edge.pulse.data.dto.OpenSessionRequest;
import com.edge.pulse.data.models.AnonIdentity;
import com.edge.pulse.data.models.Form;
import com.edge.pulse.data.models.ResponseSession;
import com.edge.pulse.repositories.FormRepository;
import com.edge.pulse.repositories.ResponseSessionRepository;
import com.edge.pulse.data.models.User;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.services.psychometric.ScoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SessionService {

    private final ResponseSessionRepository responseSessionRepository;
    private final FormRepository formRepository;
    private final UserRepository userRepository;
    private final AnonIdentityService anonIdentityService;
    private final FormCacheService cacheService;
    private final ScoringService scoringService;
    private final Clock clock;
    private final SessionCreationHelper sessionCreationHelper;
    private final CacheTtlProperties cacheTtlProps;

    public ResponseSession openOrResumeSession(UUID formId, UUID userId, OpenSessionRequest request) {
        Form form = formRepository.findById(formId)
                .orElseThrow(() -> new IllegalArgumentException("Form not found: " + formId));

        if (request.anonymous()) {
            return openOrResumeAnonymousSession(form, userId);
        } else {
            return openOrResumeIdentifiedSession(form, userId);
        }
    }

    private ResponseSession openOrResumeIdentifiedSession(Form form, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        String cacheKey = FormCacheService.openSessionKey(userId, form.getId());

        // Check cache for existing open session ID
        Optional<UUID> cachedSessionId = cacheService.get(cacheKey, UUID.class);
        if (cachedSessionId.isPresent()) {
            Optional<ResponseSession> cached = responseSessionRepository.findById(cachedSessionId.get());
            if (cached.isPresent() && cached.get().getCompletedAt() == null) {
                return cached.get();
            }
            // Stale cache entry — evict and fall through
            cacheService.evict(cacheKey);
        }

        // Check DB for existing open session (findFirst — safe against concurrent opens)
        Optional<ResponseSession> existing = responseSessionRepository
                .findFirstByUserIdAndFormIdAndCompletedAtIsNullOrderByStartedAtDesc(userId, form.getId());
        if (existing.isPresent()) {
            cacheService.put(cacheKey, existing.get().getId(), cacheTtlProps.sessionTtl());
            return existing.get();
        }

        ResponseSession built = ResponseSession.builder()
                .form(form)
                .user(user)
                .isAnonymous(false)
                .startedAt(LocalDateTime.now())
                .build();
        ResponseSession session;
        try {
            session = sessionCreationHelper.tryCreate(built);
        } catch (DataIntegrityViolationException e) {
            // Concurrent request won the race — the unique index prevented a duplicate.
            // Find and lock the winning session to prevent concurrent completeSession()
            // from closing it between our find and our return.
            session = responseSessionRepository
                    .findFirstOpenForUpdate(userId, form.getId(), PageRequest.of(0, 1))
                    .stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Session race: no open session found after concurrent creation conflict"));
        }
        cacheService.put(cacheKey, session.getId(), cacheTtlProps.sessionTtl());

        // Evict user assignments cache since status changed to IN_PROGRESS
        cacheService.evict(FormCacheService.userAssignmentsKey(userId));

        return session;
    }

    private ResponseSession openOrResumeAnonymousSession(Form form, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        UUID orgUnitId = user.getOrgUnit() != null ? user.getOrgUnit().getId() : null;
        if (orgUnitId == null) {
            throw new IllegalStateException("User must belong to an org unit for anonymous sessions");
        }

        AnonIdentity identity = anonIdentityService.resolveOrCreate(
                form.getId(), orgUnitId, null, form.getAnonWindowMinutes());

        // Check for existing open session with this identity
        Optional<ResponseSession> existing = responseSessionRepository
                .findByAnonIdentityIdAndCompletedAtIsNull(identity.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        ResponseSession session = ResponseSession.builder()
                .form(form)
                .anonIdentity(identity)
                .isAnonymous(true)
                .startedAt(LocalDateTime.now())
                .build();
        return responseSessionRepository.save(session);
    }

    public ResponseSession completeSession(UUID sessionId, UUID userId) {
        ResponseSession session = responseSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        verifyOwnership(session, userId);
        if (session.getCompletedAt() != null) {
            throw new IllegalStateException("Session already completed");
        }
        enforceTimeLimit(session);
        session.setCompletedAt(LocalDateTime.now());
        session = responseSessionRepository.save(session);

        // Trigger psychometric scoring (no-op if session is not a psychometric test)
        scoringService.scoreSession(session);

        // Evict open session cache
        if (session.getUser() != null) {
            cacheService.evict(FormCacheService.openSessionKey(
                    session.getUser().getId(), session.getForm().getId()));
            // Evict user assignments cache since status changed to COMPLETED
            cacheService.evict(FormCacheService.userAssignmentsKey(session.getUser().getId()));
        }

        return session;
    }

    @Transactional(readOnly = true)
    public ResponseSession getSession(UUID sessionId, UUID userId) {
        ResponseSession session = responseSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        verifyOwnership(session, userId);
        return session;
    }

    /**
     * Enforces the server-side time limit for timed psychometric sessions.
     *
     * <p>A 30-second grace period is added to account for network latency and clock skew.
     * Sessions with no {@code timeLimitSecs} or no {@code serverStartEpoch} are untimed
     * and pass through unconditionally.
     *
     * @throws IllegalStateException if the time limit has been exceeded
     */
    private void enforceTimeLimit(ResponseSession session) {
        if (session.getTimeLimitSecs() == null || session.getServerStartEpoch() == null) {
            return; // Untimed session — no enforcement needed
        }
        long allowedMs = (long) session.getTimeLimitSecs() * 1000L + 30_000L; // 30s grace
        long elapsedMs = Instant.now(clock).toEpochMilli() - session.getServerStartEpoch();
        if (elapsedMs > allowedMs) {
            throw new IllegalStateException(
                    "Session time limit exceeded — elapsed " + elapsedMs + "ms, limit " + allowedMs + "ms");
        }
    }

    private void verifyOwnership(ResponseSession session, UUID userId) {
        if (session.isAnonymous()) {
            return; // Anonymous sessions have no user link — skip ownership check
        }
        if (session.getUser() == null || !session.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Session does not belong to authenticated user");
        }
    }
}
