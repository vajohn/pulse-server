package com.edge.pulse.services;

import com.edge.pulse.data.dto.AddQuestionRequest;
import com.edge.pulse.data.dto.CandidateAnswerDto;
import com.edge.pulse.data.dto.CreateFormRequest;
import com.edge.pulse.data.dto.UpdateQuestionRequest;
import com.edge.pulse.data.models.CandidateAnswer;
import com.edge.pulse.data.models.Form;
import com.edge.pulse.data.models.Question;
import com.edge.pulse.data.models.User;
import com.edge.pulse.repositories.CandidateAnswerRepository;
import com.edge.pulse.repositories.FormAssignmentRepository;
import com.edge.pulse.repositories.FormRepository;
import com.edge.pulse.repositories.QuestionRepository;
import com.edge.pulse.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SurveyService {

    private final FormRepository formRepository;
    private final QuestionRepository questionRepository;
    private final CandidateAnswerRepository candidateAnswerRepository;
    private final FormCacheService cacheService;
    private final FormAssignmentRepository formAssignmentRepository;
    private final UserRepository userRepository;

    public Form createForm(CreateFormRequest request) {
        Form form = Form.builder()
                .title(request.title())
                .description(request.description())
                .anonWindowMinutes(request.anonWindowMinutes() != null ? request.anonWindowMinutes() : 60)
                .build();
        return formRepository.save(form);
    }

    @Transactional(readOnly = true)
    public Form getForm(UUID id) {
        return formRepository.findByIdWithQuestions(id)
                .orElseThrow(() -> new IllegalArgumentException("Form not found: " + id));
    }

    /**
     * Loads the form for a regular user, verifying they have an active assignment.
     * Throws AccessDeniedException if no visible assignment exists.
     */
    @Transactional(readOnly = true)
    public Form getFormForUser(UUID formId, UUID userId) {
        Form form = formRepository.findById(formId)
                .orElseThrow(() -> new IllegalArgumentException("Form not found: " + formId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        String orgPath = user.getOrgUnit() != null ? user.getOrgUnit().getPath() : "";
        if (!formAssignmentRepository.hasVisibleAssignment(formId, userId, orgPath)) {
            throw new AccessDeniedException("No active assignment for form: " + formId);
        }

        return form;
    }

    @Transactional(readOnly = true)
    public List<Question> getActiveQuestions(UUID formId) {
        // Direct query — no Redis cache here.
        // The previous strategy (cache List<UUID>, re-fetch full objects via findAllById on
        // every cache hit) still performed a DB round-trip on each hit and provided no
        // meaningful latency benefit. A proper questions cache requires a serializable DTO
        // to avoid circular JPA references and will be added as a follow-up.
        return questionRepository.findActiveByFormId(formId);
    }

    public Question addQuestion(UUID formId, AddQuestionRequest request) {
        Form form = getForm(formId);

        Question question = Question.builder()
                .form(form)
                .body(request.body())
                .bodyAr(request.bodyAr())
                .questionType(request.questionType())
                .effectiveDate(request.effectiveDate())
                .expirationDate(request.expirationDate())
                .displayOrder(request.displayOrder())
                .subjectLabels(request.subjectLabels())
                .subjectLabelsAr(request.subjectLabelsAr())
                .scaleMin(request.scaleMin())
                .scaleMax(request.scaleMax())
                .minLabel(request.minLabel())
                .minLabelAr(request.minLabelAr())
                .maxLabel(request.maxLabel())
                .maxLabelAr(request.maxLabelAr())
                .forcedChoicePairs(request.forcedChoicePairs())
                .candidateAnswers(new ArrayList<>())
                .build();
        question = questionRepository.save(question);

        if (request.candidateAnswers() != null) {
            for (CandidateAnswerDto dto : request.candidateAnswers()) {
                CandidateAnswer ca = CandidateAnswer.builder()
                        .question(question)
                        .label(dto.label())
                        .labelAr(dto.labelAr())
                        .displayOrder(dto.displayOrder())
                        .build();
                candidateAnswerRepository.save(ca);
                question.getCandidateAnswers().add(ca);
            }
        }

        // Pre-emptive eviction for the active-questions cache key.
        // getActiveQuestions() currently queries the DB directly (no Redis cache) due to
        // circular JPA references preventing safe entity serialisation. These evict calls are
        // safe no-ops today and will become effective once a serialisable QuestionCacheDto
        // cache is added — keeping them here avoids missing an invalidation site later.
        cacheService.evict(FormCacheService.activeQuestionsKey(formId));

        return question;
    }

    public Question expireQuestion(UUID formId, UUID questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));
        if (!question.getForm().getId().equals(formId)) {
            throw new IllegalArgumentException("Question does not belong to form");
        }
        question.setExpirationDate(LocalDateTime.now());
        question = questionRepository.save(question);

        // Pre-emptive eviction for the active-questions cache key.
        // getActiveQuestions() currently queries the DB directly (no Redis cache) due to
        // circular JPA references preventing safe entity serialisation. These evict calls are
        // safe no-ops today and will become effective once a serialisable QuestionCacheDto
        // cache is added — keeping them here avoids missing an invalidation site later.
        cacheService.evict(FormCacheService.activeQuestionsKey(formId));

        return question;
    }

    public void hardDeleteQuestion(UUID formId, UUID questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));
        if (!question.getForm().getId().equals(formId)) {
            throw new IllegalArgumentException("Question does not belong to form");
        }
        questionRepository.deleteById(questionId);
        // Pre-emptive eviction — see addQuestion comment for rationale.
        cacheService.evict(FormCacheService.activeQuestionsKey(formId));
    }

    // -----------------------------------------------------------------------
    // Admin CRUD
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Form> listForms() {
        return formRepository.findAllWithQuestions();
    }

    public Form updateForm(UUID id, CreateFormRequest request) {
        Form form = getForm(id);
        form.setTitle(request.title());
        form.setDescription(request.description());
        if (request.anonWindowMinutes() != null) {
            form.setAnonWindowMinutes(request.anonWindowMinutes());
        }
        return formRepository.save(form);
    }

    public void deleteForm(UUID id) {
        Form form = getForm(id);
        formRepository.delete(form);
    }

    @Transactional(readOnly = true)
    public List<Question> getAllQuestions(UUID formId) {
        return questionRepository.findByFormIdOrderByDisplayOrderAsc(formId);
    }

    public Question updateQuestion(UUID formId, UUID questionId, UpdateQuestionRequest request) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found"));
        if (!question.getForm().getId().equals(formId)) {
            throw new IllegalArgumentException("Question does not belong to form: " + formId);
        }
        question.setBody(request.body());
        if (request.bodyAr() != null) question.setBodyAr(request.bodyAr());
        if (request.questionType() != null) question.setQuestionType(request.questionType());
        question.setDisplayOrder(request.displayOrder());
        if (request.expirationDate() != null) question.setExpirationDate(request.expirationDate());
        if (request.scaleMin() != null) question.setScaleMin(request.scaleMin());
        if (request.scaleMax() != null) question.setScaleMax(request.scaleMax());
        if (request.minLabel() != null) question.setMinLabel(request.minLabel());
        if (request.minLabelAr() != null) question.setMinLabelAr(request.minLabelAr());
        if (request.maxLabel() != null) question.setMaxLabel(request.maxLabel());
        if (request.maxLabelAr() != null) question.setMaxLabelAr(request.maxLabelAr());
        if (request.forcedChoicePairs() != null) question.setForcedChoicePairs(request.forcedChoicePairs());
        if (request.subjectLabels() != null) question.setSubjectLabels(request.subjectLabels());
        if (request.subjectLabelsAr() != null) question.setSubjectLabelsAr(request.subjectLabelsAr());
        // Pre-emptive eviction — see addQuestion comment for rationale.
        cacheService.evict(FormCacheService.activeQuestionsKey(formId));
        return questionRepository.save(question);
    }
}
