package com.edge.pulse.strategy;

import com.edge.pulse.data.dto.SubmitAnswerRequest;
import com.edge.pulse.data.models.AnswerSubmission;
import com.edge.pulse.data.enums.QuestionType;
import com.edge.pulse.data.models.answer.AnswerRating;
import com.edge.pulse.repositories.answer.AnswerRatingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RatingAnswerStrategyTest {

    @Mock
    private AnswerRatingRepository answerRatingRepository;

    @InjectMocks
    private RatingAnswerStrategy strategy;

    @Test
    void supportedType_returnsRating() {
        assertThat(strategy.supportedType()).isEqualTo(QuestionType.RATING);
    }

    @Test
    void persist_savesMultipleRatings() {
        var submission = AnswerSubmission.builder().id(UUID.randomUUID()).build();
        var ratings = List.of(
                new SubmitAnswerRequest.RatingEntry("Waiter", 4, 5),
                new SubmitAnswerRequest.RatingEntry("Chef", 5, 5),
                new SubmitAnswerRequest.RatingEntry("Valet", 3, 5)
        );
        var request = new SubmitAnswerRequest(UUID.randomUUID(), QuestionType.RATING,
                null, null, null, null, null, ratings, null, null);
        when(answerRatingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        strategy.persist(submission, request);

        ArgumentCaptor<AnswerRating> captor = ArgumentCaptor.forClass(AnswerRating.class);
        verify(answerRatingRepository, times(3)).save(captor.capture());

        List<AnswerRating> saved = captor.getAllValues();
        assertThat(saved).hasSize(3);
        assertThat(saved.get(0).getSubjectLabel()).isEqualTo("Waiter");
        assertThat(saved.get(0).getStars()).isEqualTo(4);
        assertThat(saved.get(1).getSubjectLabel()).isEqualTo("Chef");
        assertThat(saved.get(1).getStars()).isEqualTo(5);
        assertThat(saved.get(2).getSubjectLabel()).isEqualTo("Valet");
        assertThat(saved.get(2).getStars()).isEqualTo(3);
    }

    @Test
    void persist_throwsWhenRatingsEmpty() {
        var submission = AnswerSubmission.builder().id(UUID.randomUUID()).build();
        var request = new SubmitAnswerRequest(UUID.randomUUID(), QuestionType.RATING,
                null, null, null, null, null, List.of(), null, null);

        assertThatThrownBy(() -> strategy.persist(submission, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void persist_throwsWhenStarsExceedMax() {
        var submission = AnswerSubmission.builder().id(UUID.randomUUID()).build();
        var ratings = List.of(
                new SubmitAnswerRequest.RatingEntry("Waiter", 6, 5)
        );
        var request = new SubmitAnswerRequest(UUID.randomUUID(), QuestionType.RATING,
                null, null, null, null, null, ratings, null, null);

        assertThatThrownBy(() -> strategy.persist(submission, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void persist_acceptsEmptySubjectLabel() {
        // RATING questions (single unlabeled star) fall back to "" as the subject key.
        // The DB column is VARCHAR(256) NOT NULL — empty string is valid.
        var submission = AnswerSubmission.builder().id(UUID.randomUUID()).build();
        var ratings = List.of(
                new SubmitAnswerRequest.RatingEntry("", 4, 5)
        );
        var request = new SubmitAnswerRequest(UUID.randomUUID(), QuestionType.RATING,
                null, null, null, null, null, ratings, null, null);
        when(answerRatingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        strategy.persist(submission, request);

        ArgumentCaptor<AnswerRating> captor = ArgumentCaptor.forClass(AnswerRating.class);
        verify(answerRatingRepository).save(captor.capture());
        assertThat(captor.getValue().getSubjectLabel()).isEqualTo("");
    }
}
