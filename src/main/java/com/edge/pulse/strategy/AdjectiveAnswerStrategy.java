package com.edge.pulse.strategy;

import com.edge.pulse.data.dto.SubmitAnswerRequest;
import com.edge.pulse.data.enums.QuestionType;
import com.edge.pulse.data.models.AnswerAdjective;
import com.edge.pulse.data.models.AnswerSubmission;
import com.edge.pulse.repositories.answer.AnswerAdjectiveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Persists adjective-checklist answers as a JSONB array of selected label strings
 * in the {@code answer_adjective} table.
 *
 * <p>At most one {@link AnswerAdjective} row exists per submission (OneToOne).
 * The scoring engine uses the count of selected adjectives × item weight.
 */
@Component
@RequiredArgsConstructor
public class AdjectiveAnswerStrategy implements AnswerPersistenceStrategy {

    private final AnswerAdjectiveRepository answerAdjectiveRepository;

    @Override
    public QuestionType supportedType() {
        return QuestionType.ADJECTIVE_CHECKLIST;
    }

    @Override
    public void persist(AnswerSubmission submission, SubmitAnswerRequest request) {
        List<String> adjectives = request.selectedAdjectives();
        if (adjectives == null) {
            adjectives = List.of();
        }

        AnswerAdjective answer = AnswerAdjective.builder()
                .submission(submission)
                .selected(adjectives)
                .build();
        answerAdjectiveRepository.save(answer);
    }

    @Override
    public Object load(AnswerSubmission submission) {
        return answerAdjectiveRepository.findBySubmissionId(submission.getId()).orElse(null);
    }
}
