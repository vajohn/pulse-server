package com.edge.pulse.mappers;

import com.edge.pulse.data.dto.CandidateAnswerDto;
import com.edge.pulse.data.dto.FormDto;
import com.edge.pulse.data.dto.QuestionDto;
import com.edge.pulse.data.models.CandidateAnswer;
import com.edge.pulse.data.models.Form;
import com.edge.pulse.data.models.Question;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Component
public class FormMapper {

    public FormDto toDto(Form entity) {
        List<QuestionDto> questions = entity.getQuestions() != null
                ? entity.getQuestions().stream().map(this::toQuestionDto).toList()
                : Collections.emptyList();
        return new FormDto(
                entity.getId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getAnonWindowMinutes(),
                questions,
                entity.getFormType() != null ? entity.getFormType().name() : "SURVEY"
        );
    }

    public FormDto toDto(Form entity, List<Question> questions) {
        return new FormDto(
                entity.getId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getAnonWindowMinutes(),
                questions.stream().map(this::toQuestionDto).toList(),
                entity.getFormType() != null ? entity.getFormType().name() : "SURVEY"
        );
    }

    public QuestionDto toQuestionDto(Question entity) {
        List<CandidateAnswerDto> candidates = entity.getCandidateAnswers() != null
                ? entity.getCandidateAnswers().stream()
                    .map(this::toCandidateDto)
                    .toList()
                : Collections.emptyList();
        boolean archived = entity.getExpirationDate() != null
                && entity.getExpirationDate().isBefore(LocalDateTime.now());
        return new QuestionDto(
                entity.getId(),
                entity.getBody(),
                entity.getBodyAr(),
                entity.getQuestionType(),
                entity.getDisplayOrder(),
                entity.getEffectiveDate(),
                entity.getExpirationDate(),
                candidates,
                entity.getSubjectLabels(),
                entity.getSubjectLabelsAr(),
                archived,
                entity.getScaleMin(),
                entity.getScaleMax(),
                entity.getMinLabel(),
                entity.getMinLabelAr(),
                entity.getMaxLabel(),
                entity.getMaxLabelAr(),
                entity.getForcedChoicePairs()
        );
    }

    public CandidateAnswerDto toCandidateDto(CandidateAnswer entity) {
        return new CandidateAnswerDto(
                entity.getId(),
                entity.getLabel(),
                entity.getLabelAr(),
                entity.getDisplayOrder()
        );
    }
}
