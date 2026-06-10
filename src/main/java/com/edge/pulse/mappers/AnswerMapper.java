package com.edge.pulse.mappers;

import com.edge.pulse.data.dto.AnswerDto;
import com.edge.pulse.data.models.AnswerChoice;
import com.edge.pulse.data.models.AnswerScale;
import com.edge.pulse.data.models.AnswerSubmission;
import com.edge.pulse.data.models.answer.AnswerRating;
import com.edge.pulse.data.models.answer.AnswerText;
import com.edge.pulse.repositories.answer.AnswerChoiceRepository;
import com.edge.pulse.repositories.answer.AnswerRatingRepository;
import com.edge.pulse.repositories.answer.AnswerScaleRepository;
import com.edge.pulse.repositories.answer.AnswerTextRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AnswerMapper {

    private final AnswerTextRepository answerTextRepository;
    private final AnswerScaleRepository answerScaleRepository;
    private final AnswerChoiceRepository answerChoiceRepository;
    private final AnswerRatingRepository answerRatingRepository;

    public AnswerDto toDto(AnswerSubmission submission) {
        return switch (submission.getAnswerType()) {
            case TEXT -> mapText(submission);
            case SCALE -> mapScale(submission);
            case CHOICE, CHOICE_SINGLE -> mapChoice(submission);
            case RATING, MULTI_RATING -> mapRating(submission);
            // ADJECTIVE_CHECKLIST and FORCED_CHOICE are stored as SCALE rows
            // (via AnswerStrategyResolver) until dedicated tables are added.
            // CHOICE_MULTIPLE is submitted as individual CHOICE entries by the client.
            case ADJECTIVE_CHECKLIST, FORCED_CHOICE -> mapScale(submission);
            case CHOICE_MULTIPLE -> mapChoice(submission);
        };
    }

    /**
     * Batch version: loads all payloads in bulk (4 queries total for any list size)
     * instead of one query per submission.
     */
    public List<AnswerDto> toDtoList(List<AnswerSubmission> submissions) {
        if (submissions.isEmpty()) return List.of();

        List<UUID> ids = submissions.stream().map(AnswerSubmission::getId).toList();

        Map<UUID, AnswerText> texts = answerTextRepository.findBySubmissionIdIn(ids)
                .stream().collect(Collectors.toMap(t -> t.getSubmission().getId(), t -> t));
        Map<UUID, AnswerScale> scales = answerScaleRepository.findBySubmissionIdIn(ids)
                .stream().collect(Collectors.toMap(s -> s.getSubmission().getId(), s -> s));
        Map<UUID, AnswerChoice> choices = answerChoiceRepository.findBySubmissionIdIn(ids)
                .stream().collect(Collectors.toMap(c -> c.getSubmission().getId(), c -> c));
        Map<UUID, List<AnswerRating>> ratings = answerRatingRepository.findBySubmissionIdIn(ids)
                .stream().collect(Collectors.groupingBy(r -> r.getSubmission().getId()));

        return submissions.stream().map(sub -> switch (sub.getAnswerType()) {
            case TEXT -> mapText(sub, texts.get(sub.getId()));
            case SCALE -> mapScale(sub, scales.get(sub.getId()));
            case CHOICE, CHOICE_SINGLE -> mapChoice(sub, choices.get(sub.getId()));
            case RATING, MULTI_RATING -> mapRating(sub, ratings.getOrDefault(sub.getId(), List.of()));
            // ADJECTIVE_CHECKLIST and FORCED_CHOICE are stored as SCALE rows.
            // CHOICE_MULTIPLE: client sends individual CHOICE rows per selection, so
            // answer_submission.answer_type is always CHOICE at the DB level. This branch
            // handles only hypothetical direct CHOICE_MULTIPLE submissions.
            case ADJECTIVE_CHECKLIST, FORCED_CHOICE -> mapScale(sub, scales.get(sub.getId()));
            case CHOICE_MULTIPLE -> mapChoice(sub, choices.get(sub.getId()));
        }).toList();
    }

    private AnswerDto mapText(AnswerSubmission sub, AnswerText at) {
        return new AnswerDto(
                sub.getId(), sub.getQuestion().getId(), sub.getAnswerType(),
                sub.getVersion(), sub.isCurrent(), sub.getSubmittedAt(),
                at != null ? at.getValue() : null,
                null, null, null, null, null, null,
                sub.getComment()
        );
    }

    private AnswerDto mapScale(AnswerSubmission sub, AnswerScale as) {
        return new AnswerDto(
                sub.getId(), sub.getQuestion().getId(), sub.getAnswerType(),
                sub.getVersion(), sub.isCurrent(), sub.getSubmittedAt(),
                null,
                as != null ? as.getValue() : null,
                as != null ? as.getMinValue() : null,
                as != null ? as.getMaxValue() : null,
                null, null, null,
                sub.getComment()
        );
    }

    private AnswerDto mapChoice(AnswerSubmission sub, AnswerChoice ac) {
        return new AnswerDto(
                sub.getId(), sub.getQuestion().getId(), sub.getAnswerType(),
                sub.getVersion(), sub.isCurrent(), sub.getSubmittedAt(),
                null, null, null, null,
                ac != null ? ac.getCandidateAnswer().getId() : null,
                ac != null ? ac.getCandidateAnswer().getLabel() : null,
                null,
                sub.getComment()
        );
    }

    private AnswerDto mapRating(AnswerSubmission sub, List<AnswerRating> ratingList) {
        List<AnswerDto.RatingEntry> entries = ratingList.stream()
                .map(r -> new AnswerDto.RatingEntry(r.getSubjectLabel(), r.getStars(), r.getMaxStars()))
                .toList();
        return new AnswerDto(
                sub.getId(), sub.getQuestion().getId(), sub.getAnswerType(),
                sub.getVersion(), sub.isCurrent(), sub.getSubmittedAt(),
                null, null, null, null, null, null,
                entries,
                sub.getComment()
        );
    }

    private AnswerDto mapText(AnswerSubmission sub) {
        AnswerText at = answerTextRepository.findBySubmissionId(sub.getId()).orElse(null);
        return new AnswerDto(
                sub.getId(), sub.getQuestion().getId(), sub.getAnswerType(),
                sub.getVersion(), sub.isCurrent(), sub.getSubmittedAt(),
                at != null ? at.getValue() : null,
                null, null, null, null, null, null,
                sub.getComment()
        );
    }

    private AnswerDto mapScale(AnswerSubmission sub) {
        AnswerScale as = answerScaleRepository.findBySubmissionId(sub.getId()).orElse(null);
        return new AnswerDto(
                sub.getId(), sub.getQuestion().getId(), sub.getAnswerType(),
                sub.getVersion(), sub.isCurrent(), sub.getSubmittedAt(),
                null,
                as != null ? as.getValue() : null,
                as != null ? as.getMinValue() : null,
                as != null ? as.getMaxValue() : null,
                null, null, null,
                sub.getComment()
        );
    }

    private AnswerDto mapChoice(AnswerSubmission sub) {
        AnswerChoice ac = answerChoiceRepository.findBySubmissionId(sub.getId()).orElse(null);
        return new AnswerDto(
                sub.getId(), sub.getQuestion().getId(), sub.getAnswerType(),
                sub.getVersion(), sub.isCurrent(), sub.getSubmittedAt(),
                null, null, null, null,
                ac != null ? ac.getCandidateAnswer().getId() : null,
                ac != null ? ac.getCandidateAnswer().getLabel() : null,
                null,
                sub.getComment()
        );
    }

    private AnswerDto mapRating(AnswerSubmission sub) {
        List<AnswerRating> ratings = answerRatingRepository.findBySubmissionId(sub.getId());
        List<AnswerDto.RatingEntry> entries = ratings.stream()
                .map(r -> new AnswerDto.RatingEntry(r.getSubjectLabel(), r.getStars(), r.getMaxStars()))
                .toList();
        return new AnswerDto(
                sub.getId(), sub.getQuestion().getId(), sub.getAnswerType(),
                sub.getVersion(), sub.isCurrent(), sub.getSubmittedAt(),
                null, null, null, null, null, null,
                entries,
                sub.getComment()
        );
    }
}
