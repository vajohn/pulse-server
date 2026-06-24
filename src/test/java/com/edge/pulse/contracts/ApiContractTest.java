package com.edge.pulse.contracts;

import com.edge.pulse.data.dto.*;
import com.edge.pulse.data.dto.psychometric.CandidateTestDto;
import com.edge.pulse.data.enums.TranslationProvider;
import com.edge.pulse.data.dto.psychometric.CandidateTestResultDetailsDto;
import com.edge.pulse.data.dto.psychometric.CandidateTestResultDto;
import com.edge.pulse.data.dto.psychometric.CompetencyScoreDto;
import com.edge.pulse.data.dto.psychometric.HeartbeatResponse;
import com.edge.pulse.data.dto.psychometric.NormEntryDto;
import com.edge.pulse.data.dto.psychometric.PsychometricQuestionDto;
import com.edge.pulse.data.dto.psychometric.ScoringKeyItemDto;
import com.edge.pulse.data.dto.psychometric.PsychometricScaleDto;
import com.edge.pulse.data.dto.psychometric.PsychometricSessionDto;
import com.edge.pulse.data.dto.psychometric.PsychometricTestAnalyticsDto;
import com.edge.pulse.data.dto.psychometric.PsychometricTestDto;
import com.edge.pulse.data.dto.psychometric.ScaleScoreDto;
import com.edge.pulse.data.dto.psychometric.TestResultSummaryDto;
import com.edge.pulse.data.enums.AssignmentStatus;
import com.edge.pulse.data.enums.TestResultStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API Contract Tests — Spring provider side.
 *
 * Verifies that each Spring DTO serializes to exactly the JSON shape that
 * Flutter's {@code fromJson()} methods expect. These tests catch field-name
 * mismatches, enum serialization differences, and type incompatibilities
 * between the two stacks before they reach production.
 *
 * Companion to {@code app/pulse/test/contract/api_contract_test.dart}.
 */
class ApiContractTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // -----------------------------------------------------------------------
    // AnalyticsSummaryDto ↔ Flutter AnalyticsSummary
    // -----------------------------------------------------------------------

    @Test
    void analyticsSummaryDto_serializes_overallAverageScore_not_overallAverage() throws Exception {
        AnalyticsSummaryDto dto = new AnalyticsSummaryDto(
                42,
                3.85,
                Map.of("Leadership", 4.2, "Wellbeing", 3.5),
                Map.of("Leadership", 30, "Wellbeing", 12),
                List.of(new AnalyticsSummaryDto.OrgUnitScoreDto("Engineering", 4.1, 20)),
                true,
                25,
                17
        );

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        // Flutter reads json['overallAverageScore'] — must NOT be 'overallAverage'
        assertThat(node.has("overallAverageScore"))
                .as("field must be 'overallAverageScore', matching Flutter AnalyticsSummary.fromJson")
                .isTrue();
        assertThat(node.get("overallAverageScore").asDouble()).isEqualTo(3.85);
        assertThat(node.has("overallAverage"))
                .as("'overallAverage' must not appear — would cause Flutter deserialization to return 0.0")
                .isFalse();
    }

    @Test
    void analyticsSummaryDto_serializes_all_contract_fields() throws Exception {
        AnalyticsSummaryDto dto = new AnalyticsSummaryDto(
                10, 3.5,
                Map.of("Cat A", 3.5), Map.of("Cat A", 10),
                List.of(), true, 6, 4
        );

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(node.has("totalRespondents")).isTrue();
        assertThat(node.has("overallAverageScore")).isTrue();
        assertThat(node.has("averageByCategory")).isTrue();
        assertThat(node.has("respondentsByCategory")).isTrue();
        assertThat(node.has("orgUnitScores")).isTrue();
        assertThat(node.has("thresholdMet")).isTrue();
        assertThat(node.has("anonymousRespondents")).isTrue();
        assertThat(node.has("identifiedRespondents")).isTrue();

        assertThat(node.get("totalRespondents").asInt()).isEqualTo(10);
        assertThat(node.get("thresholdMet").asBoolean()).isTrue();
        assertThat(node.get("anonymousRespondents").asInt()).isEqualTo(6);
        assertThat(node.get("identifiedRespondents").asInt()).isEqualTo(4);
    }

    @Test
    void analyticsSummaryDto_orgUnitScore_serializes_correct_field_names() throws Exception {
        AnalyticsSummaryDto.OrgUnitScoreDto score =
                new AnalyticsSummaryDto.OrgUnitScoreDto("Engineering", 4.1, 20);

        JsonNode node = mapper.readTree(mapper.writeValueAsString(score));

        // Flutter OrgUnitScore.fromJson reads 'orgUnitName', 'averageScore', 'respondentCount'
        assertThat(node.has("orgUnitName")).isTrue();
        assertThat(node.has("averageScore")).isTrue();
        assertThat(node.has("respondentCount")).isTrue();
        assertThat(node.get("orgUnitName").asText()).isEqualTo("Engineering");
        assertThat(node.get("respondentCount").asInt()).isEqualTo(20);
    }

    // -----------------------------------------------------------------------
    // SessionDto ↔ Flutter SurveySession
    // Critical: @JsonProperty("isAnonymous") must not strip to "anonymous"
    // -----------------------------------------------------------------------

    @Test
    void sessionDto_isAnonymous_serializes_with_correct_field_name() throws Exception {
        SessionDto dto = new SessionDto(
                UUID.randomUUID(), UUID.randomUUID(),
                true,
                LocalDateTime.now(), null,
                List.of());

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        // Flutter reads json['isAnonymous'] — must be exactly that key (F-CC-14)
        assertThat(node.has("isAnonymous"))
                .as("isAnonymous must be present — @JsonProperty annotation required on SessionDto")
                .isTrue();
        assertThat(node.has("anonymous"))
                .as("'anonymous' must not appear — would break Flutter SurveySession.fromJson")
                .isFalse();
        assertThat(node.get("isAnonymous").asBoolean()).isTrue();
    }

    @Test
    void sessionDto_serializes_startedAt_field_name() throws Exception {
        // Flutter SurveySession.fromJson reads json['startedAt'] for createdAt
        LocalDateTime startTime = LocalDateTime.of(2026, 3, 2, 9, 0, 0);
        SessionDto dto = new SessionDto(
                UUID.randomUUID(), UUID.randomUUID(),
                false, startTime, null, List.of());

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(node.has("startedAt")).isTrue();
        assertThat(node.get("startedAt").asText()).contains("2026-03-02");
    }

    @Test
    void sessionDto_serializes_all_contract_fields() throws Exception {
        SessionDto dto = new SessionDto(
                UUID.randomUUID(), UUID.randomUUID(),
                false, LocalDateTime.now(), null, List.of());

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(node.has("id")).isTrue();
        assertThat(node.has("formId")).isTrue();
        assertThat(node.has("isAnonymous")).isTrue();
        assertThat(node.has("startedAt")).isTrue();
        // completedAt may be null — verify it is present (not omitted)
        assertThat(node.has("completedAt")).isTrue();
        assertThat(node.has("currentAnswers")).isTrue();
    }

    // -----------------------------------------------------------------------
    // MyAssignmentDto ↔ Flutter Assignment
    // Critical: AssignmentStatus enum values (SCREAMING_SNAKE_CASE)
    // -----------------------------------------------------------------------

    @Test
    void myAssignmentDto_status_serializes_as_SCREAMING_SNAKE_CASE() throws Exception {
        // Flutter Assignment._parseStatus() expects: 'PENDING', 'IN_PROGRESS',
        // 'COMPLETED', 'OVERDUE', 'RETAKEABLE'
        for (AssignmentStatus status : AssignmentStatus.values()) {
            MyAssignmentDto dto = new MyAssignmentDto(
                    UUID.randomUUID(), UUID.randomUUID(),
                    null,
                    "Test Survey", "Description",
                    false, null, null, null, false,
                    status, null, null, null, 0, 5, "SURVEY");

            JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

            String serializedStatus = node.get("status").asText();
            assertThat(serializedStatus)
                    .as("Status %s must serialize as SCREAMING_SNAKE_CASE to match Flutter _parseStatus()", status)
                    .isEqualTo(status.name());
        }
    }

    @Test
    void myAssignmentDto_serializes_all_contract_fields() throws Exception {
        MyAssignmentDto dto = new MyAssignmentDto(
                UUID.randomUUID(), UUID.randomUUID(),
                null,
                "Q1 Survey", "A quarterly survey",
                true,
                LocalDateTime.of(2026, 3, 1, 0, 0),
                LocalDateTime.of(2026, 3, 31, 23, 59),
                null, false,
                AssignmentStatus.PENDING,
                null, null, null, 0, 10, "SURVEY");

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        // All fields Flutter Assignment.fromJson reads
        assertThat(node.has("assignmentId")).isTrue();
        assertThat(node.has("formId")).isTrue();
        // testId is nullable — must be present as null for SURVEY type, not absent
        assertThat(node.has("testId")).isTrue();
        assertThat(node.get("testId").isNull()).isTrue();
        assertThat(node.has("formTitle")).isTrue();
        assertThat(node.has("formDescription")).isTrue();
        assertThat(node.has("mandatory")).isTrue();
        assertThat(node.has("startsAt")).isTrue();
        assertThat(node.has("expiresAt")).isTrue();
        assertThat(node.has("allowResubmission")).isTrue();
        assertThat(node.has("status")).isTrue();
        assertThat(node.has("answeredCount")).isTrue();
        assertThat(node.has("totalQuestions")).isTrue();
        // Flutter Assignment.fromJson reads json['formType'] to decide survey vs psychometric rendering
        assertThat(node.has("formType"))
                .as("formType must be present — Flutter uses it to distinguish SURVEY from PSYCHOMETRIC")
                .isTrue();
        assertThat(node.get("formType").asText()).isEqualTo("SURVEY");
    }

    // -----------------------------------------------------------------------
    // UserSummary ↔ Flutter PulseUser
    // -----------------------------------------------------------------------

    @Test
    void userSummary_serializes_all_contract_fields() throws Exception {
        UserSummary dto = new UserSummary(
                UUID.fromString("bb000000-0000-0000-0000-000000000001"),
                "hr.admin@edge.ae",
                "HR Admin",
                "Human Resources",
                List.of("HR_FULL_CRUD"),
                List.of("USER_READ", "REPORT_VIEW"),
                UUID.fromString("aa000000-0000-0000-0000-000000000001"),
                "HQ");

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        // Flutter PulseUser.fromJson reads these exact field names
        assertThat(node.has("id")).isTrue();
        assertThat(node.has("email")).isTrue();
        assertThat(node.has("displayName")).isTrue();
        assertThat(node.has("department")).isTrue();
        assertThat(node.has("roles")).isTrue();
        assertThat(node.has("permissions")).isTrue();
        assertThat(node.has("orgUnitId")).isTrue();
        assertThat(node.has("orgUnitName")).isTrue();

        assertThat(node.get("email").asText()).isEqualTo("hr.admin@edge.ae");
        assertThat(node.get("displayName").asText()).isEqualTo("HR Admin");
        assertThat(node.get("roles").isArray()).isTrue();
        assertThat(node.get("roles").get(0).asText()).isEqualTo("HR_FULL_CRUD");
        assertThat(node.get("permissions").isArray()).isTrue();
    }

    // -----------------------------------------------------------------------
    // CandidateTestDto ↔ Flutter CandidateTest
    // -----------------------------------------------------------------------

    @Test
    void candidateTestDto_serializes_timeLimitSecs_asNullable() throws Exception {
        CandidateTestDto dto = new CandidateTestDto(
                UUID.randomUUID(), UUID.randomUUID(),
                "Big-Five", "Personality test", "Answer honestly.",
                "PERSONALITY", null, 20);

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(node.has("testId")).isTrue();
        assertThat(node.has("formId")).isTrue();
        assertThat(node.has("name")).isTrue();
        assertThat(node.has("instructions")).isTrue();
        assertThat(node.has("testType")).isTrue();
        assertThat(node.has("questionCount")).isTrue();
        // timeLimitSecs is nullable — must be present as null, not absent
        assertThat(node.has("timeLimitSecs")).isTrue();
        assertThat(node.get("timeLimitSecs").isNull()).isTrue();
        assertThat(node.get("testType").asText()).isEqualTo("PERSONALITY");
        assertThat(node.get("questionCount").asInt()).isEqualTo(20);
    }

    // -----------------------------------------------------------------------
    // CandidateTestResultDto ↔ Flutter CandidateTestResult
    // -----------------------------------------------------------------------

    @Test
    void candidateTestResultDto_serializes_enumStatus_asString() throws Exception {
        CandidateTestResultDto dto = new CandidateTestResultDto(
                UUID.randomUUID(), UUID.randomUUID(),
                "Big-Five", "PERSONALITY",
                TestResultStatus.SCORED,
                LocalDateTime.of(2026, 3, 1, 10, 0),
                LocalDateTime.of(2026, 3, 1, 10, 1),
                0);

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(node.has("resultId")).isTrue();
        assertThat(node.has("testId")).isTrue();
        assertThat(node.has("testName")).isTrue();
        assertThat(node.has("testType")).isTrue();
        assertThat(node.has("status")).isTrue();
        assertThat(node.has("completedAt")).isTrue();
        assertThat(node.has("scoredAt")).isTrue();
        assertThat(node.has("focusLossCount")).isTrue();
        // Status must be enum name string, not ordinal
        assertThat(node.get("status").asText()).isEqualTo("SCORED");
    }

    // -----------------------------------------------------------------------
    // CandidateTestResultDetailsDto ↔ Flutter CandidateTestResultDetails
    // -----------------------------------------------------------------------

    @Test
    void candidateTestResultDetailsDto_scalesIsEmptyList_whenBreakdownHidden() throws Exception {
        CandidateTestResultDetailsDto dto = new CandidateTestResultDetailsDto(
                UUID.randomUUID(), UUID.randomUUID(),
                "Big-Five", "PERSONALITY",
                TestResultStatus.SCORED,
                LocalDateTime.of(2026, 3, 1, 10, 0),
                LocalDateTime.of(2026, 3, 1, 10, 1),
                0,
                false, false, false, false,
                List.of(), false, List.of());

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(node.has("rawScoreVisible")).isTrue();
        assertThat(node.has("stenProfileVisible")).isTrue();
        assertThat(node.has("percentileVisible")).isTrue();
        assertThat(node.has("scaleBreakdownVisible")).isTrue();
        assertThat(node.has("scales")).isTrue();
        assertThat(node.get("scales").isArray()).isTrue();
        assertThat(node.get("scales").size()).isEqualTo(0);
        assertThat(node.get("rawScoreVisible").asBoolean()).isFalse();
        assertThat(node.get("scaleBreakdownVisible").asBoolean()).isFalse();
    }

    // -----------------------------------------------------------------------
    // CompetencyScoreDto ↔ Flutter CompetencyScore
    // -----------------------------------------------------------------------

    @Test
    void competencyScoreDto_serializes_score_as_decimal() throws Exception {
        CompetencyScoreDto dto = new CompetencyScoreDto(
                UUID.fromString("aa000000-0000-0000-0000-000000000001"),
                "Leadership",
                new BigDecimal("7.500"));

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        // Flutter CompetencyScore.fromJson reads 'competencyId', 'name', 'score'
        assertThat(node.has("competencyId")).isTrue();
        assertThat(node.has("name")).isTrue();
        assertThat(node.has("score")).isTrue();
        assertThat(node.get("competencyId").asText())
                .isEqualTo("aa000000-0000-0000-0000-000000000001");
        assertThat(node.get("name").asText()).isEqualTo("Leadership");
        // Score must serialize as a numeric (not string) — Flutter reads as num
        assertThat(node.get("score").isNumber()).isTrue();
        assertThat(node.get("score").decimalValue())
                .isEqualByComparingTo(new BigDecimal("7.500"));
    }

    @Test
    void candidateResultDetailsDto_includes_competencyMapVisible_and_competencies() throws Exception {
        UUID compId = UUID.fromString("bb000000-0000-0000-0000-000000000002");
        CompetencyScoreDto compScore = new CompetencyScoreDto(compId, "Resilience",
                new BigDecimal("8.250"));

        CandidateTestResultDetailsDto dto = new CandidateTestResultDetailsDto(
                UUID.randomUUID(), UUID.randomUUID(),
                "Big-Five", "PERSONALITY",
                TestResultStatus.SCORED,
                LocalDateTime.of(2026, 3, 1, 10, 0),
                LocalDateTime.of(2026, 3, 1, 10, 1),
                0,
                false, true, false, false,
                List.of(),
                true,
                List.of(compScore));

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        // Flutter CandidateTestResultDetails.fromJson reads these two new fields
        assertThat(node.has("competencyMapVisible")).isTrue();
        assertThat(node.get("competencyMapVisible").asBoolean()).isTrue();
        assertThat(node.has("competencies")).isTrue();
        assertThat(node.get("competencies").isArray()).isTrue();
        assertThat(node.get("competencies").size()).isEqualTo(1);

        JsonNode cs = node.get("competencies").get(0);
        assertThat(cs.has("competencyId")).isTrue();
        assertThat(cs.has("name")).isTrue();
        assertThat(cs.has("score")).isTrue();
        assertThat(cs.get("name").asText()).isEqualTo("Resilience");
        assertThat(cs.get("score").decimalValue()).isEqualByComparingTo(new BigDecimal("8.250"));
    }

    // -----------------------------------------------------------------------
    // PsychometricSessionDto ↔ Flutter PsychometricSession
    // -----------------------------------------------------------------------

    @Test
    void psychometricSessionDto_serializes_all_contract_fields() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID q1Id = UUID.randomUUID();
        PsychometricQuestionDto question = new PsychometricQuestionDto(
                q1Id, "I handle pressure well.", null, "SCALE", 1,
                1, 5, "Strongly Disagree", "Strongly Agree", List.of(),
                false, false, null, null);

        PsychometricSessionDto dto = new PsychometricSessionDto(
                sessionId, "Resilience Profile", "PERSONALITY",
                "Answer honestly.", null, null, null,
                List.of(q1Id), List.of(question));

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        // Flutter PsychometricSession.fromJson reads all of these exact keys
        assertThat(node.has("sessionId")).isTrue();
        assertThat(node.has("testName")).isTrue();
        assertThat(node.has("testType")).isTrue();
        assertThat(node.has("instructions")).isTrue();
        // nullable fields must be present as JSON null, not absent
        assertThat(node.has("timeLimitSecs")).isTrue();
        assertThat(node.get("timeLimitSecs").isNull()).isTrue();
        assertThat(node.has("remainingSeconds")).isTrue();
        assertThat(node.get("remainingSeconds").isNull()).isTrue();
        assertThat(node.has("serverStartEpoch")).isTrue();
        assertThat(node.has("itemSequence")).isTrue();
        assertThat(node.has("questions")).isTrue();

        assertThat(node.get("testName").asText()).isEqualTo("Resilience Profile");
        assertThat(node.get("testType").asText()).isEqualTo("PERSONALITY");
        assertThat(node.get("itemSequence").isArray()).isTrue();
        assertThat(node.get("questions").isArray()).isTrue();
        assertThat(node.get("questions").size()).isEqualTo(1);
    }

    @Test
    void psychometricSessionDto_timedCognitive_serializes_timer_fields() throws Exception {
        PsychometricSessionDto dto = new PsychometricSessionDto(
                UUID.randomUUID(), "Cognitive Battery", "COGNITIVE",
                null, 1800, 1750L, 1700000000000L,
                List.of(), List.of());

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(node.get("testType").asText()).isEqualTo("COGNITIVE");
        assertThat(node.get("timeLimitSecs").asInt()).isEqualTo(1800);
        assertThat(node.get("remainingSeconds").asLong()).isEqualTo(1750L);
        assertThat(node.get("serverStartEpoch").asLong()).isEqualTo(1700000000000L);
    }

    // -----------------------------------------------------------------------
    // PsychometricQuestionDto ↔ Flutter PsychometricQuestion
    // -----------------------------------------------------------------------

    @Test
    void psychometricQuestionDto_scale_serializes_rangeFields() throws Exception {
        UUID qId = UUID.randomUUID();
        PsychometricQuestionDto dto = new PsychometricQuestionDto(
                qId, "I stay calm under pressure.", null, "SCALE", 1,
                1, 5, "Strongly Disagree", "Strongly Agree", List.of(),
                false, false, null, null);

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(node.has("id")).isTrue();
        assertThat(node.has("body")).isTrue();
        assertThat(node.has("questionType")).isTrue();
        assertThat(node.has("displayOrder")).isTrue();
        assertThat(node.has("scaleMin")).isTrue();
        assertThat(node.has("scaleMax")).isTrue();
        assertThat(node.has("minLabel")).isTrue();
        assertThat(node.has("maxLabel")).isTrue();
        assertThat(node.has("candidateAnswers")).isTrue();
        assertThat(node.get("questionType").asText()).isEqualTo("SCALE");
        assertThat(node.get("scaleMin").asInt()).isEqualTo(1);
        assertThat(node.get("scaleMax").asInt()).isEqualTo(5);
        assertThat(node.get("minLabel").asText()).isEqualTo("Strongly Disagree");
    }

    @Test
    void psychometricQuestionDto_choice_nullableScaleFields_serializedAsNull() throws Exception {
        // CHOICE questions have null scale fields — Flutter reads them as int?/String?
        CandidateAnswerDto answer = new CandidateAnswerDto(UUID.randomUUID(), "Option A", null, 1);
        PsychometricQuestionDto dto = new PsychometricQuestionDto(
                UUID.randomUUID(), "Which best describes you?", null, "CHOICE", 2,
                null, null, null, null, List.of(answer),
                false, false, null, null);

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        // nullable scale fields must be present as null (not absent)
        assertThat(node.has("scaleMin")).isTrue();
        assertThat(node.get("scaleMin").isNull()).isTrue();
        assertThat(node.has("scaleMax")).isTrue();
        assertThat(node.get("scaleMax").isNull()).isTrue();
        assertThat(node.has("minLabel")).isTrue();
        assertThat(node.get("minLabel").isNull()).isTrue();

        // candidateAnswers contains the choice options
        assertThat(node.get("candidateAnswers").isArray()).isTrue();
        assertThat(node.get("candidateAnswers").size()).isEqualTo(1);
        JsonNode ca = node.get("candidateAnswers").get(0);
        assertThat(ca.has("id")).isTrue();
        assertThat(ca.has("label")).isTrue();
        assertThat(ca.has("displayOrder")).isTrue();
        assertThat(ca.get("label").asText()).isEqualTo("Option A");
    }

    @Test
    void psychometricQuestionDto_choiceMultiple_serializes_allowMultipleSelect_true() throws Exception {
        CandidateAnswerDto a1 = new CandidateAnswerDto(UUID.randomUUID(), "Option A", null, 1);
        CandidateAnswerDto a2 = new CandidateAnswerDto(UUID.randomUUID(), "Option B", null, 2);
        PsychometricQuestionDto dto = new PsychometricQuestionDto(
                UUID.randomUUID(), "Select all correct answers.", null, "CHOICE_MULTIPLE", 1,
                null, null, null, null, List.of(a1, a2),
                true, true, null, null);

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(node.get("questionType").asText()).isEqualTo("CHOICE_MULTIPLE");
        assertThat(node.get("allowMultipleSelect").asBoolean()).isTrue();
        assertThat(node.get("partialCredit").asBoolean()).isTrue();
        assertThat(node.get("candidateAnswers").size()).isEqualTo(2);
        // adjectives and forcedChoicePairs are absent (JsonInclude.NON_NULL)
        assertThat(node.has("adjectives")).isFalse();
        assertThat(node.has("forcedChoicePairs")).isFalse();
    }

    @Test
    void psychometricQuestionDto_adjectiveChecklist_serializes_adjectives() throws Exception {
        PsychometricQuestionDto dto = new PsychometricQuestionDto(
                UUID.randomUUID(), "Tap words that describe you.", null, "ADJECTIVE_CHECKLIST", 1,
                null, null, null, null, List.of(),
                false, false, List.of("Confident", "Empathetic", "Driven"), null);

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(node.get("questionType").asText()).isEqualTo("ADJECTIVE_CHECKLIST");
        assertThat(node.has("adjectives")).isTrue();
        assertThat(node.get("adjectives").isArray()).isTrue();
        assertThat(node.get("adjectives").size()).isEqualTo(3);
        assertThat(node.get("adjectives").get(0).asText()).isEqualTo("Confident");
        assertThat(node.has("forcedChoicePairs")).isFalse();
    }

    @Test
    void psychometricQuestionDto_forcedChoice_serializes_pairs() throws Exception {
        UUID scaleAId = UUID.randomUUID();
        UUID scaleBId = UUID.randomUUID();
        var pair = Map.<String, Object>of(
                "a", "I enjoy leading teams",   "scaleA", scaleAId.toString(),
                "b", "I prefer working alone",  "scaleB", scaleBId.toString());
        PsychometricQuestionDto dto = new PsychometricQuestionDto(
                UUID.randomUUID(), null, null, "FORCED_CHOICE", 1,
                null, null, null, null, List.of(),
                false, false, null, List.of(pair));

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(node.get("questionType").asText()).isEqualTo("FORCED_CHOICE");
        assertThat(node.has("forcedChoicePairs")).isTrue();
        assertThat(node.get("forcedChoicePairs").isArray()).isTrue();
        assertThat(node.get("forcedChoicePairs").size()).isEqualTo(1);
        JsonNode p = node.get("forcedChoicePairs").get(0);
        assertThat(p.has("a")).isTrue();
        assertThat(p.has("b")).isTrue();
        assertThat(p.has("scaleA")).isTrue();
        assertThat(p.has("scaleB")).isTrue();
        assertThat(p.get("a").asText()).isEqualTo("I enjoy leading teams");
        assertThat(node.has("adjectives")).isFalse();
    }

    // -----------------------------------------------------------------------
    // HeartbeatResponse ↔ Flutter psychometric_api.heartbeat()
    // -----------------------------------------------------------------------

    @Test
    void heartbeatResponse_null_for_untimed_session() throws Exception {
        HeartbeatResponse dto = new HeartbeatResponse(null);
        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        // Flutter reads data['remainingSeconds'] as int? — must be present as null
        assertThat(node.has("remainingSeconds")).isTrue();
        assertThat(node.get("remainingSeconds").isNull()).isTrue();
    }

    @Test
    void heartbeatResponse_integer_for_timed_session() throws Exception {
        HeartbeatResponse dto = new HeartbeatResponse(480L);
        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(node.has("remainingSeconds")).isTrue();
        assertThat(node.get("remainingSeconds").asLong()).isEqualTo(480L);
    }

    // -----------------------------------------------------------------------
    // PsychometricTestDto ↔ Flutter PsychometricTestAdmin
    // -----------------------------------------------------------------------

    @Test
    void psychometricTestDto_serializes_all_fields_including_nullableTimelimit() throws Exception {
        PsychometricTestDto dto = new PsychometricTestDto(
                UUID.randomUUID(), UUID.randomUUID(),
                "Big-Five Inventory", "Personality test", "Answer honestly.",
                "PERSONALITY", null, "DRAFT", 1,
                LocalDateTime.of(2026, 3, 1, 9, 0), 20, 3
        );

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(node.has("testId")).isTrue();
        assertThat(node.has("surveyId")).isTrue();
        assertThat(node.has("name")).isTrue();
        assertThat(node.has("description")).isTrue();
        assertThat(node.has("instructions")).isTrue();
        assertThat(node.has("testType")).isTrue();
        assertThat(node.has("status")).isTrue();
        assertThat(node.has("version")).isTrue();
        assertThat(node.has("createdAt")).isTrue();
        assertThat(node.has("questionCount")).isTrue();
        assertThat(node.has("scaleCount")).isTrue();
        // timeLimitSecs nullable — must be present as null, not absent
        assertThat(node.has("timeLimitSecs")).isTrue();
        assertThat(node.get("timeLimitSecs").isNull()).isTrue();
        assertThat(node.get("testType").asText()).isEqualTo("PERSONALITY");
        assertThat(node.get("status").asText()).isEqualTo("DRAFT");
        // TestStatus uses DRAFT / ACTIVE / RETIRED (not PUBLISHED/ARCHIVED)
        assertThat(node.get("questionCount").asInt()).isEqualTo(20);
        assertThat(node.get("scaleCount").asInt()).isEqualTo(3);
    }

    @Test
    void psychometricScaleDto_serializes_parentScaleId_as_null_when_absent() throws Exception {
        PsychometricScaleDto dto = new PsychometricScaleDto(
                UUID.randomUUID(), UUID.randomUUID(),
                null,  // parentScaleId absent
                "Life Control", "Control over life events",
                "AVERAGE", 1,
                null,  // compositeMethod absent
                null,  // compositeBasis absent
                null   // compositeRoundingScale absent
        );

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(node.has("scaleId")).isTrue();
        assertThat(node.has("testId")).isTrue();
        assertThat(node.has("name")).isTrue();
        assertThat(node.has("description")).isTrue();
        assertThat(node.has("scoreMethod")).isTrue();
        assertThat(node.has("displayOrder")).isTrue();
        // parentScaleId nullable — must be present as null
        assertThat(node.has("parentScaleId")).isTrue();
        assertThat(node.get("parentScaleId").isNull()).isTrue();
        assertThat(node.get("scoreMethod").asText()).isEqualTo("AVERAGE");
        assertThat(node.get("displayOrder").asInt()).isEqualTo(1);
    }

    @Test
    void testResultSummaryDto_serializes_all_fields_including_nullable_dates() throws Exception {
        TestResultSummaryDto dto = new TestResultSummaryDto(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "Jane Smith", "Extraversion Test",
                TestResultStatus.SCORED,
                LocalDateTime.of(2026, 3, 1, 10, 1),
                null,  // reviewedAt absent
                null,  // reviewNotes absent
                2,
                UUID.randomUUID(), null
        );

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(node.has("resultId")).isTrue();
        assertThat(node.has("testId")).isTrue();
        assertThat(node.has("userId")).isTrue();
        assertThat(node.has("userName")).isTrue();
        assertThat(node.has("testName")).isTrue();
        assertThat(node.get("testName").asText()).isEqualTo("Extraversion Test");
        assertThat(node.has("status")).isTrue();
        assertThat(node.has("scoredAt")).isTrue();
        assertThat(node.has("focusLossCount")).isTrue();
        // nullable fields must be present as null, not absent
        assertThat(node.has("reviewedAt")).isTrue();
        assertThat(node.get("reviewedAt").isNull()).isTrue();
        assertThat(node.has("reviewNotes")).isTrue();
        assertThat(node.get("reviewNotes").isNull()).isTrue();
        assertThat(node.get("status").asText()).isEqualTo("SCORED");
        assertThat(node.get("focusLossCount").asInt()).isEqualTo(2);
    }

    @Test
    void psychometricTestAnalyticsDto_stenHistogram_serializes_as_10_element_array() throws Exception {
        // stenHistogram is long[] — Jackson must serialize it as a JSON array, not drop it
        var scaleDto = new PsychometricTestAnalyticsDto.ScaleAnalyticsDto(
                UUID.randomUUID(), 20L,
                3.5, 6.2, 70.1, 1.2,
                new long[]{0L, 1L, 2L, 5L, 6L, 3L, 2L, 1L, 0L, 0L}  // 10 elements
        );
        var dto = new PsychometricTestAnalyticsDto(
                UUID.randomUUID(), 20L, 5L, 12L, 2L, 1L,
                LocalDateTime.of(2026, 3, 1, 9, 0),
                1.3,
                List.of(scaleDto)
        );

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(node.get("totalResults").asLong()).isEqualTo(20L);
        assertThat(node.get("pendingCount").asLong()).isEqualTo(5L);
        assertThat(node.get("scoredCount").asLong()).isEqualTo(12L);
        assertThat(node.get("scaleStats").isArray()).isTrue();
        assertThat(node.get("scaleStats").size()).isEqualTo(1);

        JsonNode scale = node.get("scaleStats").get(0);
        assertThat(scale.has("scaleId")).isTrue();
        assertThat(scale.has("resultCount")).isTrue();
        assertThat(scale.has("avgRawScore")).isTrue();
        assertThat(scale.has("avgSten")).isTrue();
        assertThat(scale.has("avgPercentile")).isTrue();
        assertThat(scale.has("stddevRawScore")).isTrue();

        // Critical: stenHistogram must serialize as a JSON array, never dropped
        assertThat(scale.has("stenHistogram")).isTrue();
        assertThat(scale.get("stenHistogram").isArray()).isTrue();
        assertThat(scale.get("stenHistogram").size()).isEqualTo(10);
        // index 0 = sten-1 count, index 9 = sten-10 count
        assertThat(scale.get("stenHistogram").get(0).asLong()).isEqualTo(0L);
        assertThat(scale.get("stenHistogram").get(2).asLong()).isEqualTo(2L);
        assertThat(scale.get("stenHistogram").get(4).asLong()).isEqualTo(6L);
        assertThat(scale.get("stenHistogram").get(9).asLong()).isEqualTo(0L);
    }

    // -----------------------------------------------------------------------
    // AdminRoleDto ↔ Flutter AdminRole / RolePermission
    // -----------------------------------------------------------------------

    @Test
    void adminRoleDto_serializes_expected_shape() throws Exception {
        AdminRoleDto dto = new AdminRoleDto(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "HR_FULL_CRUD",
                List.of(new PermissionDto("USR_READ", "View user profiles", "USR"),
                        new PermissionDto("SCOPE_ORG_WIDE", "Full org visibility", "SCOPE")),
                5);

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        // Flutter AdminRole.fromJson reads: id, name, permissions, userCount
        assertThat(node.get("id").asText()).isEqualTo("00000000-0000-0000-0000-000000000001");
        assertThat(node.get("name").asText()).isEqualTo("HR_FULL_CRUD");
        assertThat(node.get("userCount").asInt()).isEqualTo(5);
        assertThat(node.get("permissions").isArray()).isTrue();
        assertThat(node.get("permissions").size()).isEqualTo(2);
    }

    @Test
    void permissionDto_serializes_name_description_group() throws Exception {
        PermissionDto dto = new PermissionDto("USR_READ", "View user profiles, search the directory", "USR");

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        // Flutter RolePermission.fromJson reads: name, description, group
        assertThat(node.get("name").asText()).isEqualTo("USR_READ");
        assertThat(node.get("description").asText()).isEqualTo("View user profiles, search the directory");
        assertThat(node.get("group").asText()).isEqualTo("USR");
    }

    @Test
    void permissionDto_groupOf_derivesPrefix() {
        assertThat(PermissionDto.groupOf("USR_READ")).isEqualTo("USR");
        assertThat(PermissionDto.groupOf("SCOPE_ORG_WIDE")).isEqualTo("SCOPE");
        assertThat(PermissionDto.groupOf("ASSESS_COMPETENCY_MANAGE")).isEqualTo("ASSESS");
        assertThat(PermissionDto.groupOf("NOUNDERSCORE")).isEqualTo("NOUNDERSCORE");
    }

    // -----------------------------------------------------------------------
    // RBAC: PermissionName enum — all 55 values must have non-blank descriptions
    // -----------------------------------------------------------------------

    @Test
    void permissionName_allValues_haveNonBlankDescriptions() {
        for (com.edge.pulse.data.enums.PermissionName pn :
                com.edge.pulse.data.enums.PermissionName.values()) {
            assertThat(pn.getDescription())
                    .as("PermissionName.%s must have a non-blank description", pn.name())
                    .isNotBlank();
        }
    }

    @Test
    void userSummary_nullable_org_fields_serialize_as_null_not_absent() throws Exception {
        // Flutter reads json['orgUnitId'] as String? — null is fine, absent would
        // cause a cast exception if the field is required at the call site
        UserSummary dto = new UserSummary(
                UUID.randomUUID(), "user@edge.ae",
                null, null,
                List.of("EMPLOYEE"), List.of(),
                null, null);

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        // Jackson records serialize null fields by default — assert they're present
        assertThat(node.has("orgUnitId")).isTrue();
        assertThat(node.get("orgUnitId").isNull()).isTrue();
        assertThat(node.has("orgUnitName")).isTrue();
        assertThat(node.get("orgUnitName").isNull()).isTrue();
    }

    // -----------------------------------------------------------------------
    // Phase 1 — TestTypeCapabilities / PsychometricTestDto contract
    // -----------------------------------------------------------------------

    @Test
    void psychometricTestDto_testType_serializes_as_screaming_snake_string() throws Exception {
        // COGNITIVE
        PsychometricTestDto cognitive = new PsychometricTestDto(
                UUID.randomUUID(), UUID.randomUUID(),
                "Spatial Reasoning", null, null,
                "COGNITIVE", 1800, "DRAFT", 1,
                LocalDateTime.of(2026, 3, 1, 9, 0), 10, 2
        );
        JsonNode cogNode = mapper.readTree(mapper.writeValueAsString(cognitive));
        assertThat(cogNode.get("testType").asText())
                .as("testType must be SCREAMING_SNAKE string 'COGNITIVE', not ordinal")
                .isEqualTo("COGNITIVE");
        // COGNITIVE has timeLimitSecs — must NOT serialize as null
        assertThat(cogNode.get("timeLimitSecs").asInt()).isEqualTo(1800);

        // PERSONALITY
        PsychometricTestDto personality = new PsychometricTestDto(
                UUID.randomUUID(), UUID.randomUUID(),
                "Big-Five", null, null,
                "PERSONALITY", null, "ACTIVE", 1,
                LocalDateTime.of(2026, 3, 1, 9, 0), 30, 5
        );
        JsonNode pNode = mapper.readTree(mapper.writeValueAsString(personality));
        assertThat(pNode.get("testType").asText()).isEqualTo("PERSONALITY");
        // PERSONALITY is untimed — timeLimitSecs must be present as JSON null
        assertThat(pNode.has("timeLimitSecs")).isTrue();
        assertThat(pNode.get("timeLimitSecs").isNull()).isTrue();
    }

    // ── QuestionDto contract tests (admin form question management) ──────────

    @Test
    void questionDto_scale_serializes_range_and_label_fields() throws Exception {
        QuestionDto dto = new QuestionDto(
                UUID.randomUUID(), "I stay calm under pressure.", null, com.edge.pulse.data.enums.QuestionType.SCALE,
                1, null, null, List.of(), null, null, false,
                1, 5, "Strongly Disagree", null, "Strongly Agree", null, null);
        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));
        assertThat(node.get("scaleMin").asInt()).isEqualTo(1);
        assertThat(node.get("scaleMax").asInt()).isEqualTo(5);
        assertThat(node.get("minLabel").asText()).isEqualTo("Strongly Disagree");
        assertThat(node.get("maxLabel").asText()).isEqualTo("Strongly Agree");
        // forcedChoicePairs is null → @JsonInclude(NON_NULL) must omit the field
        assertThat(node.has("forcedChoicePairs")).isFalse();
    }

    @Test
    void questionDto_forcedChoice_serializes_pairs_and_omits_scale_fields() throws Exception {
        List<Map<String, Object>> pairs = List.of(Map.of(
                "a", "I enjoy leading teams", "b", "I prefer working alone",
                "traitA", "extraversion", "traitB", "introversion"));
        QuestionDto dto = new QuestionDto(
                UUID.randomUUID(), "Choose the more accurate statement", null,
                com.edge.pulse.data.enums.QuestionType.FORCED_CHOICE,
                1, null, null, List.of(), null, null, false,
                null, null, null, null, null, null, pairs);
        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));
        assertThat(node.has("forcedChoicePairs")).isTrue();
        assertThat(node.get("forcedChoicePairs").isArray()).isTrue();
        assertThat(node.get("forcedChoicePairs").size()).isEqualTo(1);
        // null scale fields must serialize as JSON null so Flutter reads int? correctly
        assertThat(node.get("scaleMin").isNull()).isTrue();
        assertThat(node.get("scaleMax").isNull()).isTrue();
    }

    @Test
    void questionDto_nonScale_omits_forcedChoicePairs_field() throws Exception {
        QuestionDto dto = new QuestionDto(
                UUID.randomUUID(), "Select prime numbers", null,
                com.edge.pulse.data.enums.QuestionType.CHOICE_MULTIPLE,
                1, null, null, List.of(), null, null, false,
                null, null, null, null, null, null, null);
        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));
        // forcedChoicePairs is null — @JsonInclude(NON_NULL) must omit the field entirely
        assertThat(node.has("forcedChoicePairs")).isFalse();
    }

    @Test
    void psychometricTestDto_status_serializes_as_screaming_snake_string() throws Exception {
        // Verify all three status values serialize correctly — Flutter maps these
        // to TestStatus.draft / active / retired constants
        for (var status : new String[]{"DRAFT", "ACTIVE", "RETIRED"}) {
            PsychometricTestDto dto = new PsychometricTestDto(
                    UUID.randomUUID(), UUID.randomUUID(),
                    "Test", null, null,
                    "COGNITIVE", 900, status, 1,
                    LocalDateTime.of(2026, 3, 1, 9, 0), 0, 0
            );
            JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));
            assertThat(node.get("status").asText())
                    .as("status must serialize as SCREAMING_SNAKE for status: " + status)
                    .isEqualTo(status);
        }
    }

    // ── ScoringKeyItemDto ↔ Flutter ScoringKeyItemAdmin ──────────────────────

    @Test
    void scoringKeyItemDto_serializes_all_fields() throws Exception {
        // Flutter ScoringKeyItemAdmin.fromJson reads:
        //   questionId, questionBody, questionType, scaleId, scaleName,
        //   direction, weight, correctAnswerId, correctAnswerLabel, partialCredit
        // — all must be present with correct names. partialCredit is a boolean
        // record component (not a getter), so Jackson serializes it as "partialCredit"
        // (NOT "isPartialCredit").
        UUID questionId = UUID.randomUUID();
        UUID scaleId    = UUID.randomUUID();
        UUID answerId   = UUID.randomUUID();

        ScoringKeyItemDto dto = new ScoringKeyItemDto(
                questionId, "What is 2+2?", "CHOICE_SINGLE",
                scaleId, "Verbal Reasoning",
                "FORWARD", BigDecimal.valueOf(1.5),
                answerId, "4",
                true
        );
        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(node.get("questionId").asText()).isEqualTo(questionId.toString());
        assertThat(node.get("questionBody").asText()).isEqualTo("What is 2+2?");
        assertThat(node.get("questionType").asText()).isEqualTo("CHOICE_SINGLE");
        assertThat(node.get("scaleId").asText()).isEqualTo(scaleId.toString());
        assertThat(node.get("scaleName").asText()).isEqualTo("Verbal Reasoning");
        assertThat(node.get("direction").asText()).isEqualTo("FORWARD");
        assertThat(node.get("weight").decimalValue()).isEqualByComparingTo(BigDecimal.valueOf(1.5));
        assertThat(node.get("correctAnswerId").asText()).isEqualTo(answerId.toString());
        assertThat(node.get("correctAnswerLabel").asText()).isEqualTo("4");
        // Must be "partialCredit" — NOT "isPartialCredit" (record component, not bean getter)
        assertThat(node.has("partialCredit")).isTrue();
        assertThat(node.has("isPartialCredit")).isFalse();
        assertThat(node.get("partialCredit").asBoolean()).isTrue();
    }

    @Test
    void scoringKeyItemDto_nullable_fields_present_as_null_when_no_correct_answer() throws Exception {
        ScoringKeyItemDto dto = new ScoringKeyItemDto(
                UUID.randomUUID(), "Rate your confidence", "SCALE",
                UUID.randomUUID(), "Extraversion",
                "FORWARD", BigDecimal.ONE,
                null, null,   // correctAnswerId and correctAnswerLabel are null for SCALE
                false
        );
        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        // Nullable UUID fields must be present as null (not absent) — Flutter reads them
        assertThat(node.has("correctAnswerId")).isTrue();
        assertThat(node.get("correctAnswerId").isNull()).isTrue();
        assertThat(node.has("correctAnswerLabel")).isTrue();
        assertThat(node.get("correctAnswerLabel").isNull()).isTrue();
        assertThat(node.get("partialCredit").asBoolean()).isFalse();
    }

    // ── NormEntryDto ↔ Flutter NormEntryAdmin ────────────────────────────────

    @Test
    void normEntryDto_serializes_all_fields() throws Exception {
        // Flutter NormEntryAdmin.fromJson reads:
        //   scaleId, scaleName, stenScore, rawScoreMin, rawScoreMax, percentile, zScore
        UUID scaleId = UUID.randomUUID();

        NormEntryDto dto = new NormEntryDto(
                scaleId, "Verbal Reasoning",
                new BigDecimal("7"),
                BigDecimal.valueOf(18.0), BigDecimal.valueOf(23.5),
                BigDecimal.valueOf(75.0), BigDecimal.valueOf(0.67)
        );
        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(node.get("scaleId").asText()).isEqualTo(scaleId.toString());
        assertThat(node.get("scaleName").asText()).isEqualTo("Verbal Reasoning");
        assertThat(node.get("stenScore").asInt()).isEqualTo(7);
        assertThat(node.get("rawScoreMin").decimalValue()).isEqualByComparingTo(BigDecimal.valueOf(18.0));
        assertThat(node.get("rawScoreMax").decimalValue()).isEqualByComparingTo(BigDecimal.valueOf(23.5));
        assertThat(node.get("percentile").decimalValue()).isEqualByComparingTo(BigDecimal.valueOf(75.0));
        assertThat(node.get("zScore").decimalValue()).isEqualByComparingTo(BigDecimal.valueOf(0.67));
    }

    @Test
    void normEntryDto_nullable_optional_fields_present_as_null() throws Exception {
        // percentile and zScore are optional — must serialize as null, not absent
        NormEntryDto dto = new NormEntryDto(
                UUID.randomUUID(), "Numerical", new BigDecimal("3"),
                BigDecimal.valueOf(5), BigDecimal.valueOf(9),
                null, null
        );
        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(node.has("percentile")).isTrue();
        assertThat(node.get("percentile").isNull()).isTrue();
        assertThat(node.has("zScore")).isTrue();
        assertThat(node.get("zScore").isNull()).isTrue();
    }

    // ── BatchSubmitRequest / SubmitAnswerRequest ↔ Flutter _buildAnswerPayload ─
    //
    // These are INBOUND request DTOs (Flutter → Spring). The contract test
    // verifies deserialization: that the JSON field names Flutter sends in
    // _buildAnswerPayload() are correctly read by Jackson into SubmitAnswerRequest.
    // A field name mismatch here causes a silently-null field, not a 400 error.

    @Test
    void submitAnswerRequest_scale_deserializes_all_fields() throws Exception {
        // Flutter sends: answerType, scaleValue, minValue, maxValue for SCALE questions.
        String json = """
                {"questionId":"%s","answerType":"SCALE","scaleValue":5,"minValue":1,"maxValue":7}
                """.formatted(UUID.randomUUID());

        SubmitAnswerRequest req = mapper.readValue(json, SubmitAnswerRequest.class);

        assertThat(req.answerType().name()).isEqualTo("SCALE");
        assertThat(req.scaleValue()).isEqualTo(5);
        assertThat(req.minValue()).isEqualTo(1);
        assertThat(req.maxValue()).isEqualTo(7);
        // Fields not present in SCALE payload must be null, not cause parse errors
        assertThat(req.candidateAnswerId()).isNull();
        assertThat(req.selectedAdjectives()).isNull();
    }

    @Test
    void submitAnswerRequest_choice_deserializes_candidateAnswerId() throws Exception {
        // Flutter sends: answerType=CHOICE, candidateAnswerId (UUID string) for radio/multi.
        UUID answerId = UUID.randomUUID();
        String json = """
                {"questionId":"%s","answerType":"CHOICE","candidateAnswerId":"%s"}
                """.formatted(UUID.randomUUID(), answerId);

        SubmitAnswerRequest req = mapper.readValue(json, SubmitAnswerRequest.class);

        assertThat(req.answerType().name()).isEqualTo("CHOICE");
        assertThat(req.candidateAnswerId()).isEqualTo(answerId);
        assertThat(req.scaleValue()).isNull();
        assertThat(req.selectedAdjectives()).isNull();
    }

    @Test
    void submitAnswerRequest_adjectiveChecklist_deserializes_selectedAdjectives() throws Exception {
        // Flutter sends: answerType=ADJECTIVE_CHECKLIST, selectedAdjectives as JSON array.
        // The bug this catches: the old stale encoding sent 'comment' instead — this
        // test verifies the corrected payload shape is what Spring reads.
        String json = """
                {"questionId":"%s","answerType":"ADJECTIVE_CHECKLIST",
                 "selectedAdjectives":["Self-confident","Adaptable","Driven"]}
                """.formatted(UUID.randomUUID());

        SubmitAnswerRequest req = mapper.readValue(json, SubmitAnswerRequest.class);

        assertThat(req.answerType().name()).isEqualTo("ADJECTIVE_CHECKLIST");
        assertThat(req.selectedAdjectives()).containsExactly("Self-confident", "Adaptable", "Driven");
        // comment must be null — the old stale payload sent 'comment'; verify it is not mapped
        assertThat(req.comment()).isNull();
    }

    @Test
    void submitAnswerRequest_forcedChoice_deserializes_as_scale_payload() throws Exception {
        // Flutter sends FORCED_CHOICE with scaleValue=0|1, minValue=0, maxValue=1.
        // AnswerStrategyResolver maps FORCED_CHOICE → SCALE; the payload shape must match.
        String json = """
                {"questionId":"%s","answerType":"FORCED_CHOICE","scaleValue":1,"minValue":0,"maxValue":1}
                """.formatted(UUID.randomUUID());

        SubmitAnswerRequest req = mapper.readValue(json, SubmitAnswerRequest.class);

        assertThat(req.answerType().name()).isEqualTo("FORCED_CHOICE");
        assertThat(req.scaleValue()).isEqualTo(1);
        assertThat(req.minValue()).isEqualTo(0);
        assertThat(req.maxValue()).isEqualTo(1);
        assertThat(req.candidateAnswerId()).isNull();
    }

    @Test
    void batchSubmitRequest_wraps_answers_list() throws Exception {
        // Flutter wraps individual answers in {"answers": [...]} for the complete-session call.
        UUID qId = UUID.randomUUID();
        String json = """
                {"answers":[
                  {"questionId":"%s","answerType":"SCALE","scaleValue":4,"minValue":1,"maxValue":5}
                ]}
                """.formatted(qId);

        BatchSubmitRequest req = mapper.readValue(json, BatchSubmitRequest.class);

        assertThat(req.answers()).hasSize(1);
        SubmitAnswerRequest answer = req.answers().get(0);
        assertThat(answer.questionId()).isEqualTo(qId);
        assertThat(answer.answerType().name()).isEqualTo("SCALE");
        assertThat(answer.scaleValue()).isEqualTo(4);
    }

    // -----------------------------------------------------------------------
    // TranslateResponse ↔ Flutter AdminTranslationApi
    // -----------------------------------------------------------------------

    @Test
    void translateResponse_single_fieldNames() throws Exception {
        TranslateResponse dto = new TranslateResponse("مرحبا", "AZURE", false);
        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        // Flutter reads json['translatedText'], json['provider'], json['cached']
        assertThat(node.has("translatedText")).as("must be 'translatedText'").isTrue();
        assertThat(node.get("translatedText").asText()).isEqualTo("مرحبا");
        assertThat(node.has("provider")).isTrue();
        assertThat(node.get("provider").asText()).isEqualTo("AZURE");
        assertThat(node.has("cached")).isTrue();
        assertThat(node.get("cached").asBoolean()).isFalse();
    }

    @Test
    void translateResponse_batch_fieldNames() throws Exception {
        TranslateBatchResponse dto = new TranslateBatchResponse(
                List.of("مرحبا", "مع السلامة"), "AZURE");
        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        // Flutter reads json['translatedTexts'] (plural) and json['provider']
        assertThat(node.has("translatedTexts")).as("must be 'translatedTexts'").isTrue();
        assertThat(node.get("translatedTexts").isArray()).isTrue();
        assertThat(node.get("translatedTexts").get(0).asText()).isEqualTo("مرحبا");
        assertThat(node.has("provider")).isTrue();
    }

    @Test
    void translateResponse_providerNone_serializes() throws Exception {
        TranslateResponse dto = new TranslateResponse("Hello", TranslationProvider.NONE.name(), false);
        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(node.get("provider").asText()).isEqualTo("NONE");
    }

    @Test
    void translateResponse_providerMyMemory_serializes() throws Exception {
        TranslateResponse dto = new TranslateResponse("مرحبا", TranslationProvider.MYMEMORY.name(), false);
        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        // Flutter reads provider as a raw String — name must be exactly "MYMEMORY"
        assertThat(node.get("provider").asText()).isEqualTo("MYMEMORY");
        assertThat(node.get("translatedText").asText()).isEqualTo("مرحبا");
    }

    @Test
    void translateResponse_cacheHit_serializesCachedTrue() throws Exception {
        TranslateResponse dto = new TranslateResponse("مرحبا", "AZURE", true);
        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        // Flutter reads json['cached'] as bool — must serialise as JSON boolean, not string
        assertThat(node.get("cached").isBoolean()).as("cached must be a JSON boolean").isTrue();
        assertThat(node.get("cached").asBoolean()).isTrue();
    }

    // -----------------------------------------------------------------------
    // QuestionDto _ar fields ↔ Flutter Question.fromJson
    // -----------------------------------------------------------------------

    @Test
    void questionDto_arFields_presentWhenNotNull() throws Exception {
        var candidateAnswerDto = new CandidateAnswerDto(UUID.randomUUID(), "Yes", "نعم", 0);
        var dto = new QuestionDto(
                UUID.randomUUID(), "Hello world", "مرحبا بالعالم",
                com.edge.pulse.data.enums.QuestionType.SCALE,
                1, null, null,
                List.of(candidateAnswerDto),
                List.of("Communication"), List.of("التواصل"),
                false, 1, 5,
                "Low", "منخفض",
                "High", "عالٍ",
                null
        );

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        // Flutter reads json['bodyAr'], json['minLabelAr'], json['maxLabelAr'], json['subjectLabelsAr']
        assertThat(node.has("bodyAr")).isTrue();
        assertThat(node.get("bodyAr").asText()).isEqualTo("مرحبا بالعالم");
        assertThat(node.has("minLabelAr")).isTrue();
        assertThat(node.get("minLabelAr").asText()).isEqualTo("منخفض");
        assertThat(node.has("maxLabelAr")).isTrue();
        assertThat(node.get("maxLabelAr").asText()).isEqualTo("عالٍ");
        assertThat(node.has("subjectLabelsAr")).isTrue();
        assertThat(node.get("subjectLabelsAr").get(0).asText()).isEqualTo("التواصل");

        // CandidateAnswerDto: Flutter reads json['labelAr']
        JsonNode answer = node.get("candidateAnswers").get(0);
        assertThat(answer.has("labelAr")).isTrue();
        assertThat(answer.get("labelAr").asText()).isEqualTo("نعم");
    }

    @Test
    void questionDto_arFieldsNull_omittedFromJson() throws Exception {
        var dto = new QuestionDto(
                UUID.randomUUID(), "Hello world", null,  // bodyAr = null
                com.edge.pulse.data.enums.QuestionType.TEXT,
                1, null, null,
                List.of(), null, null,  // subjectLabelsAr = null
                false, null, null,
                null, null,   // minLabelAr = null
                null, null,   // maxLabelAr = null
                null
        );

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        // @JsonInclude(NON_NULL) — null _ar fields must be absent, not "null"
        assertThat(node.has("bodyAr")).as("null bodyAr must be omitted").isFalse();
        assertThat(node.has("minLabelAr")).as("null minLabelAr must be omitted").isFalse();
        assertThat(node.has("maxLabelAr")).as("null maxLabelAr must be omitted").isFalse();
        assertThat(node.has("subjectLabelsAr")).as("null subjectLabelsAr must be omitted").isFalse();
    }
}
