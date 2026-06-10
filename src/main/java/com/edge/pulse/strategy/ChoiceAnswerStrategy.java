package com.edge.pulse.strategy;

import com.edge.pulse.data.dto.SubmitAnswerRequest;
import com.edge.pulse.data.models.AnswerSubmission;
import com.edge.pulse.data.models.CandidateAnswer;
import com.edge.pulse.data.enums.QuestionType;
import com.edge.pulse.data.models.AnswerChoice;
import com.edge.pulse.repositories.CandidateAnswerRepository;
import com.edge.pulse.repositories.answer.AnswerChoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChoiceAnswerStrategy implements AnswerPersistenceStrategy {

    private final AnswerChoiceRepository answerChoiceRepository;
    private final CandidateAnswerRepository candidateAnswerRepository;

    @Override
    public QuestionType supportedType() {
        return QuestionType.CHOICE;
    }

    @Override
    public void persist(AnswerSubmission submission, SubmitAnswerRequest request) {
        if (request.candidateAnswerId() == null) {
            throw new IllegalArgumentException("Candidate answer ID must not be null");
        }

        CandidateAnswer candidateAnswer = candidateAnswerRepository.findById(request.candidateAnswerId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Candidate answer not found: " + request.candidateAnswerId()));

        AnswerChoice answerChoice = AnswerChoice.builder()
                .submission(submission)
                .candidateAnswer(candidateAnswer)
                .build();
        answerChoiceRepository.save(answerChoice);
    }

    @Override
    public Object load(AnswerSubmission submission) {
        return answerChoiceRepository.findBySubmissionId(submission.getId()).orElse(null);
    }
}
