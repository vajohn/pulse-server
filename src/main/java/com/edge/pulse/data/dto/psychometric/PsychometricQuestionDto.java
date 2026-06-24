package com.edge.pulse.data.dto.psychometric;

import com.edge.pulse.data.dto.CandidateAnswerDto;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Question payload for the psychometric session start response.
 *
 * <p><strong>Anti-cheating contract:</strong> {@code is_correct} on
 * {@link com.edge.pulse.data.models.CandidateAnswer} is annotated {@code @JsonIgnore} and is
 * never serialized. This record never exposes correct-answer data to the candidate.
 *
 * <p>Field presence by question type:
 * <ul>
 *   <li>SCALE: {@code scaleMin}, {@code scaleMax}, {@code minLabel}, {@code maxLabel} populated</li>
 *   <li>CHOICE_SINGLE / CHOICE_MULTIPLE: {@code candidateAnswers} populated;
 *       {@code allowMultipleSelect} set accordingly</li>
 *   <li>ADJECTIVE_CHECKLIST: {@code adjectives} populated</li>
 *   <li>FORCED_CHOICE: {@code forcedChoicePairs} populated</li>
 * </ul>
 *
 * <p>Nullable fields are serialized as JSON {@code null} (not absent) so Flutter's
 * {@code fromJson()} can use {@code as int?} / {@code as String?} safely.
 */
public record PsychometricQuestionDto(
        UUID id,
        String body,
        /** Arabic translation of {@code body}. Null when no AR variant has been authored. */
        String bodyAr,
        /** SCREAMING_SNAKE string matching Spring QuestionType enum. */
        String questionType,
        int displayOrder,

        // ── SCALE fields ─────────────────────────────────────────────────────
        Integer scaleMin,
        Integer scaleMax,
        String minLabel,
        String maxLabel,

        // ── CHOICE_SINGLE / CHOICE_MULTIPLE fields ────────────────────────────
        List<CandidateAnswerDto> candidateAnswers,

        // ── CHOICE_MULTIPLE extra flags ───────────────────────────────────────
        /** true for CHOICE_MULTIPLE, false for CHOICE_SINGLE. Always present. */
        boolean allowMultipleSelect,
        /** true if the scoring key uses partial credit for this question. */
        boolean partialCredit,

        // ── ADJECTIVE_CHECKLIST fields ────────────────────────────────────────
        /** Non-null only for ADJECTIVE_CHECKLIST questions. */
        @JsonInclude(JsonInclude.Include.NON_NULL) List<String> adjectives,

        // ── FORCED_CHOICE fields ──────────────────────────────────────────────
        /** Non-null only for FORCED_CHOICE questions. Each map has keys a, scaleA, b, scaleB. */
        @JsonInclude(JsonInclude.Include.NON_NULL) List<Map<String, Object>> forcedChoicePairs
) {}
