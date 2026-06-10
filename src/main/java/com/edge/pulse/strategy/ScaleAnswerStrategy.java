package com.edge.pulse.strategy;

import com.edge.pulse.data.dto.SubmitAnswerRequest;
import com.edge.pulse.data.models.AnswerSubmission;
import com.edge.pulse.data.enums.QuestionType;
import com.edge.pulse.data.models.AnswerScale;
import com.edge.pulse.repositories.answer.AnswerScaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScaleAnswerStrategy implements AnswerPersistenceStrategy {

    private final AnswerScaleRepository answerScaleRepository;

    @Override
    public QuestionType supportedType() {
        return QuestionType.SCALE;
    }

    @Override
    public void persist(AnswerSubmission submission, SubmitAnswerRequest request) {
        if (request.scaleValue() == null) {
            throw new IllegalArgumentException("Scale value must not be null");
        }

        int minValue = request.minValue() != null ? request.minValue() : 1;
        int maxValue = request.maxValue() != null ? request.maxValue() : 5;

        if (request.scaleValue() < minValue || request.scaleValue() > maxValue) {
            throw new IllegalArgumentException(
                    "Scale value must be between " + minValue + " and " + maxValue);
        }

        AnswerScale answerScale = AnswerScale.builder()
                .submission(submission)
                .value(request.scaleValue())
                .minValue(minValue)
                .maxValue(maxValue)
                .build();
        answerScaleRepository.save(answerScale);
    }

    @Override
    public Object load(AnswerSubmission submission) {
        return answerScaleRepository.findBySubmissionId(submission.getId()).orElse(null);
    }
}
