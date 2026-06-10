package com.edge.pulse.strategy;

import com.edge.pulse.data.dto.SubmitAnswerRequest;
import com.edge.pulse.data.models.AnswerSubmission;
import com.edge.pulse.data.enums.QuestionType;
import com.edge.pulse.data.models.answer.AnswerText;
import com.edge.pulse.repositories.answer.AnswerTextRepository;
import com.edge.pulse.services.AuditService;
import com.edge.pulse.services.PiiRedactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TextAnswerStrategy implements AnswerPersistenceStrategy {

    private final AnswerTextRepository answerTextRepository;
    private final PiiRedactionService piiRedactionService;
    private final AuditService auditService;

    @Override
    public QuestionType supportedType() {
        return QuestionType.TEXT;
    }

    @Override
    public void persist(AnswerSubmission submission, SubmitAnswerRequest request) {
        if (request.textValue() == null || request.textValue().isBlank()) {
            throw new IllegalArgumentException("Text value must not be blank");
        }

        PiiRedactionService.ScanResult scan = piiRedactionService.redact(request.textValue());

        if (scan.piiDetected()) {
            // Log the event without the actual text — never log PII.
            log.warn("PII detected and redacted in text answer for session={} question={}",
                    submission.getSession().getId(), submission.getQuestion().getId());

            // User may be null for anonymous sessions — that is fine.
            var sessionUser = submission.getSession().getUser();
            auditService.logAction(
                    sessionUser != null ? sessionUser.getId() : null,
                    "PII_REDACTED",
                    "answer_submission",
                    submission.getId(),
                    "Text answer contained PII and was automatically redacted before persistence",
                    null
            );
        }

        AnswerText answerText = AnswerText.builder()
                .submission(submission)
                .value(scan.redactedText())
                .build();
        answerTextRepository.save(answerText);
    }

    @Override
    public Object load(AnswerSubmission submission) {
        return answerTextRepository.findBySubmissionId(submission.getId()).orElse(null);
    }
}
