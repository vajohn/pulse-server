package com.edge.pulse.services;

import com.edge.pulse.data.models.ResponseSession;
import com.edge.pulse.repositories.ResponseSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Package-private transaction boundary helper for response session creation.
 *
 * <p>The {@code REQUIRES_NEW} propagation ensures the INSERT into
 * {@code response_session} runs in its own transaction, committed independently
 * of the caller's outer transaction.
 *
 * <p>If the partial unique index {@code idx_session_user_form_open} fires
 * (concurrent insert for the same user + form), the child transaction rolls back
 * and the {@code DataIntegrityViolationException} propagates to the caller —
 * leaving the outer transaction untainted so it can retry with
 * {@code findFirstOpenForUpdate} to return the winning session.
 *
 * <p>Must not be made public — callers outside {@code services} do not hold
 * the outer-transaction context that makes the race-recovery catch pattern safe.
 */
@Component
@RequiredArgsConstructor
class SessionCreationHelper {

    private final ResponseSessionRepository responseSessionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    ResponseSession tryCreate(ResponseSession session) {
        return responseSessionRepository.saveAndFlush(session);
    }
}
