package com.edge.pulse.strategy;

import com.edge.pulse.data.dto.SubmitAnswerRequest;
import com.edge.pulse.data.models.AnswerSubmission;
import com.edge.pulse.data.enums.QuestionType;
import com.edge.pulse.data.models.answer.AnswerRating;
import com.edge.pulse.repositories.answer.AnswerRatingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RatingAnswerStrategy implements AnswerPersistenceStrategy {

    private final AnswerRatingRepository answerRatingRepository;

    @Override
    public QuestionType supportedType() {
        return QuestionType.RATING;
    }

    @Override
    public void persist(AnswerSubmission submission, SubmitAnswerRequest request) {
        if (request.ratings() == null || request.ratings().isEmpty()) {
            throw new IllegalArgumentException("Ratings list must not be empty for RATING answer type");
        }

        for (SubmitAnswerRequest.RatingEntry entry : request.ratings()) {
            if (entry.subjectLabel() == null) {
                throw new IllegalArgumentException("Subject label must not be null");
            }
            if (entry.stars() < 0) {
                throw new IllegalArgumentException("Stars must not be negative");
            }

            int maxStars = entry.maxStars() != null ? entry.maxStars() : 5;

            if (entry.stars() > maxStars) {
                throw new IllegalArgumentException(
                        "Stars (" + entry.stars() + ") must not exceed max stars (" + maxStars + ") for subject: " + entry.subjectLabel());
            }

            AnswerRating answerRating = AnswerRating.builder()
                    .submission(submission)
                    .subjectLabel(entry.subjectLabel())
                    .stars(entry.stars())
                    .maxStars(maxStars)
                    .build();
            answerRatingRepository.save(answerRating);
        }
    }

    @Override
    public Object load(AnswerSubmission submission) {
        return answerRatingRepository.findBySubmissionId(submission.getId());
    }
}
