package com.edge.pulse.services;

import com.edge.pulse.data.dto.SubmitAnswerRequest;
import com.edge.pulse.data.models.AnswerSubmission;
import com.edge.pulse.repositories.AnswerSubmissionRepository;
import com.edge.pulse.strategy.AnswerStrategyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Package-private transaction boundary helper for answer submission inserts.
 *
 * <p>The {@code REQUIRES_NEW} propagation ensures both the INSERT into
 * {@code answer_submission} and the strategy-level answer data
 * ({@code answer_scale}, {@code answer_choice}, etc.) commit atomically in
 * their own transaction, independent of the caller's outer transaction. Moving
 * both writes into the same child transaction eliminates the orphan-row risk
 * that would arise if the outer transaction rolled back after the submission
 * row was already committed but before its answer data was written.
 *
 * <p>If the partial unique index {@code idx_answer_submission_current} fires
 * (concurrent insert for the same session + question), the child transaction
 * rolls back — taking both the submission row and any partial answer data with
 * it — and the {@code DataIntegrityViolationException} propagates to the
 * caller, leaving the outer transaction untainted so it can fall back to
 * {@link AnswerService#versionAnswer}.
 *
 * <p>Must not be made public — callers outside {@code services} do not hold
 * the outer-transaction context that makes the race-recovery catch pattern safe.
 */
@Component
@RequiredArgsConstructor
class AnswerSubmissionCreationHelper {

    private final AnswerSubmissionRepository answerSubmissionRepository;
    private final AnswerStrategyResolver strategyResolver;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    AnswerSubmission tryInsert(AnswerSubmission submission, SubmitAnswerRequest request) {
        AnswerSubmission saved = answerSubmissionRepository.saveAndFlush(submission);
        strategyResolver.resolve(request.answerType()).persist(saved, request);
        return saved;
    }
}
