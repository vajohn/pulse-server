package com.edge.pulse.services;

import com.edge.pulse.data.dto.AnswerDto;
import com.edge.pulse.data.dto.SubmitAnswerRequest;
import com.edge.pulse.data.models.AnswerSubmission;
import com.edge.pulse.data.models.Question;
import com.edge.pulse.data.models.ResponseSession;
import com.edge.pulse.mappers.AnswerMapper;
import com.edge.pulse.repositories.AnswerSubmissionRepository;
import com.edge.pulse.repositories.QuestionRepository;
import com.edge.pulse.repositories.ResponseSessionRepository;
import com.edge.pulse.strategy.AnswerStrategyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AnswerService {

    private final AnswerSubmissionRepository answerSubmissionRepository;
    private final ResponseSessionRepository responseSessionRepository;
    private final QuestionRepository questionRepository;
    private final AnswerStrategyResolver strategyResolver;
    private final AnswerMapper answerMapper;
    private final PiiRedactionService piiRedactionService;
    private final AnswerSubmissionCreationHelper answerSubmissionCreationHelper;

    public AnswerDto submitAnswer(UUID sessionId, SubmitAnswerRequest request) {
        ResponseSession session = responseSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        if (session.getCompletedAt() != null) {
            throw new IllegalStateException("Cannot submit answers to a completed session");
        }

        Question question = questionRepository.findById(request.questionId())
                .orElseThrow(() -> new IllegalArgumentException("Question not found: " + request.questionId()));

        // Check if there's already a current answer — if so, version it instead
        Optional<AnswerSubmission> existingCurrent = answerSubmissionRepository
                .findBySessionIdAndQuestionIdAndIsCurrentTrue(sessionId, request.questionId());
        if (existingCurrent.isPresent()) {
            return versionAnswer(sessionId, request.questionId(), request);
        }

        String safeComment = request.comment() != null
                ? piiRedactionService.redact(request.comment()).redactedText() : null;

        AnswerSubmission submission = AnswerSubmission.builder()
                .session(session)
                .question(question)
                .answerType(request.answerType())
                .version(1)
                .isCurrent(true)
                .submittedAt(LocalDateTime.now())
                .comment(safeComment)
                .build();
        try {
            submission = answerSubmissionCreationHelper.tryInsert(submission, request);
        } catch (DataIntegrityViolationException e) {
            // Concurrent request inserted first for this (session, question) pair.
            // Version this answer instead — correct for both retry and true concurrent scenarios.
            return versionAnswer(sessionId, request.questionId(), request);
        }

        return answerMapper.toDto(submission);
    }

    public AnswerDto versionAnswer(UUID sessionId, UUID questionId, SubmitAnswerRequest request) {
        ResponseSession session = responseSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        if (session.getCompletedAt() != null) {
            throw new IllegalStateException("Cannot update answers in a completed session");
        }

        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));

        // Find current version number
        List<AnswerSubmission> history = answerSubmissionRepository.findVersionHistory(sessionId, questionId);
        int nextVersion = history.isEmpty() ? 1 : history.getLast().getVersion() + 1;

        // Mark previous versions as not current
        answerSubmissionRepository.markPreviousVersionsNotCurrent(sessionId, questionId);

        String safeComment = request.comment() != null
                ? piiRedactionService.redact(request.comment()).redactedText() : null;

        AnswerSubmission submission = AnswerSubmission.builder()
                .session(session)
                .question(question)
                .answerType(request.answerType())
                .version(nextVersion)
                .isCurrent(true)
                .submittedAt(LocalDateTime.now())
                .comment(safeComment)
                .build();
        submission = answerSubmissionRepository.save(submission);

        strategyResolver.resolve(request.answerType()).persist(submission, request);
        return answerMapper.toDto(submission);
    }

    public List<AnswerDto> submitAll(UUID sessionId, List<SubmitAnswerRequest> requests) {
        ResponseSession session = responseSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        if (session.getCompletedAt() != null) {
            throw new IllegalStateException("Cannot submit answers to a completed session");
        }

        return requests.stream()
                .map(request -> submitAnswer(sessionId, request))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AnswerDto> getAnswerHistory(UUID sessionId, UUID questionId) {
        List<AnswerSubmission> history = answerSubmissionRepository.findVersionHistory(sessionId, questionId);
        return history.stream().map(answerMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<AnswerDto> getCurrentAnswers(UUID sessionId) {
        List<AnswerSubmission> current = answerSubmissionRepository.findBySessionIdAndIsCurrentTrue(sessionId);
        return answerMapper.toDtoList(current);
    }

    @Transactional(readOnly = true)
    public List<AnswerDto> getPriorAnswers(UUID sessionId) {
        ResponseSession session = responseSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        if (session.isAnonymous()) {
            throw new IllegalStateException("Cannot retrieve prior answers for anonymous sessions");
        }
        if (session.getUser() == null) {
            return List.of();
        }

        // Find the last completed session for this user + questionnaire
        List<ResponseSession> completedSessions = responseSessionRepository
                .findCompletedByUserAndForm(session.getUser().getId(), session.getForm().getId());
        if (completedSessions.isEmpty()) {
            return List.of();
        }

        ResponseSession lastCompleted = completedSessions.getFirst();
        List<AnswerSubmission> currentAnswers = answerSubmissionRepository.findBySessionIdAndIsCurrentTrue(lastCompleted.getId());
        return answerMapper.toDtoList(currentAnswers);
    }
}
