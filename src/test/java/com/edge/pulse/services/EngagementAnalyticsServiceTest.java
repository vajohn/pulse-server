package com.edge.pulse.services;

import com.edge.pulse.data.dto.EngagementSummaryDto;
import com.edge.pulse.data.enums.OrgLevel;
import com.edge.pulse.data.models.OrganizationalUnit;
import com.edge.pulse.data.models.User;
import com.edge.pulse.repositories.AnalyticsMvRepository;
import com.edge.pulse.repositories.FormAssignmentRepository;
import com.edge.pulse.repositories.FormOrgSessionCountsMvRepository;
import com.edge.pulse.repositories.FormRepository;
import com.edge.pulse.repositories.FormSessionCountsMvRepository;
import com.edge.pulse.repositories.OrganizationalUnitRepository;
import com.edge.pulse.repositories.QuestionChoiceMvRepository;
import com.edge.pulse.repositories.QuestionRatingMvRepository;
import com.edge.pulse.repositories.QuestionRepository;
import com.edge.pulse.repositories.QuestionScaleMvRepository;
import com.edge.pulse.repositories.ResponseSessionRepository;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.repositories.answer.AnswerChoiceRepository;
import com.edge.pulse.repositories.answer.AnswerRatingRepository;
import com.edge.pulse.repositories.answer.AnswerScaleRepository;
import com.edge.pulse.repositories.answer.AnswerTextRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link AnalyticsService#getEngagementSummary} (PULSE-WEB-4).
 *
 * <p>Covers: correct aggregation, org-scope (includeChildren / exact) resolution,
 * caller scope-bounding, trend computation, and the mandatory server-side
 * min-team-size (&lt;5) suppression.
 */
@ExtendWith(MockitoExtension.class)
class EngagementAnalyticsServiceTest {

    @Mock private AnalyticsMvRepository mvRepo;
    @Mock private FormSessionCountsMvRepository formSessionCountsMvRepo;
    @Mock private FormOrgSessionCountsMvRepository formOrgSessionCountsMvRepo;
    @Mock private QuestionScaleMvRepository questionScaleMvRepo;
    @Mock private QuestionChoiceMvRepository questionChoiceMvRepo;
    @Mock private QuestionRatingMvRepository questionRatingMvRepo;
    @Mock private FormRepository formRepository;
    @Mock private FormAssignmentRepository assignmentRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private ResponseSessionRepository sessionRepository;
    @Mock private AnswerScaleRepository scaleRepo;
    @Mock private AnswerChoiceRepository choiceRepo;
    @Mock private AnswerRatingRepository ratingRepo;
    @Mock private AnswerTextRepository textRepo;
    @Mock private UserRepository userRepository;
    @Mock private OrganizationalUnitRepository orgUnitRepository;
    @Mock private OrgUnitScopeService orgUnitScopeService;
    @Mock private RoleChangeService roleChangeService;

    @InjectMocks
    private AnalyticsService service;

    private OrganizationalUnit orgUnit(String path) {
        return OrganizationalUnit.builder()
                .id(UUID.randomUUID())
                .orgUnitName("Ops Team")
                .orgLevel(OrgLevel.TEAM)
                .path(path)
                .active(true)
                .depth(2)
                .children(Collections.emptyList())
                .build();
    }

    private User userIn(OrganizationalUnit ou) {
        return User.builder()
                .id(UUID.randomUUID())
                .email("u@test.com")
                .orgUnit(ou)
                .build();
    }

    // -----------------------------------------------------------------------
    // Min-team-size suppression (MANDATORY)
    // -----------------------------------------------------------------------

    @Test
    void engagement_belowThreshold_returnsMaskedWithNoAggregates() {
        OrganizationalUnit ou = orgUnit("/EDGE/OPS");
        UUID userId = UUID.randomUUID();
        when(orgUnitRepository.findById(ou.getId())).thenReturn(Optional.of(ou));
        when(orgUnitScopeService.hasBroadScope(anyCollection())).thenReturn(true);
        // 4 respondents < MIN_RESPONDENTS(5) → masked
        when(scaleRepo.countScopedRespondents(eq("/EDGE/OPS"), anyBoolean(), any(LocalDateTime.class)))
                .thenReturn(4L);

        EngagementSummaryDto dto = service.getEngagementSummary("TEAM", ou.getId(), true, 30, userId);

        assertThat(dto.masked()).isTrue();
        assertThat(dto.respondents()).isNull();
        assertThat(dto.overallScore()).isNull();
        assertThat(dto.categoryScores()).isNull();
        assertThat(dto.scoreDistribution()).isNull();
        assertThat(dto.trend()).isNull();
        // scope echo still present so the client knows what was masked
        assertThat(dto.nodeId()).isEqualTo(ou.getId());
        assertThat(dto.scopeLevel()).isEqualTo("TEAM");
    }

    @Test
    void engagement_exactlyAtThreshold_returnsData() {
        OrganizationalUnit ou = orgUnit("/EDGE/OPS");
        UUID userId = UUID.randomUUID();
        when(orgUnitRepository.findById(ou.getId())).thenReturn(Optional.of(ou));
        when(orgUnitScopeService.hasBroadScope(anyCollection())).thenReturn(true);
        when(scaleRepo.countScopedRespondents(eq("/EDGE/OPS"), eq(false), any())).thenReturn(5L);
        when(scaleRepo.findScopedOverallAverage(eq("/EDGE/OPS"), eq(false), any()))
                .thenReturn(Optional.of(3.6));
        when(scaleRepo.findScopedFormAverages(eq("/EDGE/OPS"), eq(false), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"Pulse Q1", 3.6, 5}));
        when(scaleRepo.findScopedScoreDistribution(eq("/EDGE/OPS"), eq(false), any()))
                .thenReturn(List.<Object[]>of(new Object[]{4, 3L}, new Object[]{3, 2L}));
        when(userRepository.countByOrgUnitPathStartingWithAndActiveTrue("/EDGE/OPS")).thenReturn(10L);
        when(scaleRepo.countScopedRespondentsInWindow(eq("/EDGE/OPS"), eq(false), any(), any()))
                .thenReturn(6L);
        when(scaleRepo.findScopedOverallAverageInWindow(eq("/EDGE/OPS"), eq(false), any(), any()))
                .thenReturn(Optional.of(3.2));

        EngagementSummaryDto dto = service.getEngagementSummary("TEAM", ou.getId(), true, 30, userId);

        assertThat(dto.masked()).isFalse();
        assertThat(dto.respondents()).isEqualTo(5);
        assertThat(dto.overallScore()).isEqualTo(3.6);
        assertThat(dto.eligibleUsers()).isEqualTo(10);
        assertThat(dto.participationRate()).isEqualTo(50.0);
        assertThat(dto.categoryScores()).hasSize(1);
        assertThat(dto.categoryScores().get(0).category()).isEqualTo("Pulse Q1");
        assertThat(dto.scoreDistribution()).hasSize(2);
        // trend: 3.6 current vs 3.2 prior → UP
        assertThat(dto.trend().direction()).isEqualTo("UP");
        assertThat(dto.trend().delta()).isEqualTo(3.6 - 3.2);
        assertThat(dto.enps()).isNull();
    }

    // -----------------------------------------------------------------------
    // Per-form (category) suppression: sub-cohort below threshold is dropped
    // -----------------------------------------------------------------------

    @Test
    void engagement_perFormBelowThreshold_isSuppressedFromCategoryScores() {
        OrganizationalUnit ou = orgUnit("/EDGE/OPS");
        UUID userId = UUID.randomUUID();
        when(orgUnitRepository.findById(ou.getId())).thenReturn(Optional.of(ou));
        when(orgUnitScopeService.hasBroadScope(anyCollection())).thenReturn(true);
        when(scaleRepo.countScopedRespondents(any(), anyBoolean(), any())).thenReturn(8L);
        when(scaleRepo.findScopedOverallAverage(any(), anyBoolean(), any())).thenReturn(Optional.of(4.0));
        when(scaleRepo.findScopedFormAverages(any(), anyBoolean(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"Big Form", 4.0, 8},   // kept
                        new Object[]{"Tiny Form", 5.0, 3})); // dropped (<5)
        when(scaleRepo.findScopedScoreDistribution(any(), anyBoolean(), any())).thenReturn(List.of());
        when(userRepository.countByOrgUnitPathStartingWithAndActiveTrue(any())).thenReturn(20L);
        when(scaleRepo.countScopedRespondentsInWindow(any(), anyBoolean(), any(), any())).thenReturn(0L);

        EngagementSummaryDto dto = service.getEngagementSummary("TEAM", ou.getId(), true, 30, userId);

        assertThat(dto.categoryScores()).hasSize(1);
        assertThat(dto.categoryScores().get(0).category()).isEqualTo("Big Form");
        // No prior-period respondents → NO_PRIOR_DATA
        assertThat(dto.trend().direction()).isEqualTo("NO_PRIOR_DATA");
        assertThat(dto.trend().previousScore()).isNull();
    }

    // -----------------------------------------------------------------------
    // Org-scope resolution
    // -----------------------------------------------------------------------

    @Test
    void engagement_includeChildrenFalse_usesExactOnlyAndOwnUnitDenominator() {
        OrganizationalUnit ou = orgUnit("/EDGE/OPS");
        UUID userId = UUID.randomUUID();
        when(orgUnitRepository.findById(ou.getId())).thenReturn(Optional.of(ou));
        when(orgUnitScopeService.hasBroadScope(anyCollection())).thenReturn(true);
        // exactOnly = true expected when includeChildren = false
        when(scaleRepo.countScopedRespondents(eq("/EDGE/OPS"), eq(true), any())).thenReturn(6L);
        when(scaleRepo.findScopedOverallAverage(eq("/EDGE/OPS"), eq(true), any())).thenReturn(Optional.of(3.0));
        when(scaleRepo.findScopedFormAverages(eq("/EDGE/OPS"), eq(true), any())).thenReturn(List.of());
        when(scaleRepo.findScopedScoreDistribution(eq("/EDGE/OPS"), eq(true), any())).thenReturn(List.of());
        when(userRepository.countByOrgUnitIdAndActiveTrue(ou.getId())).thenReturn(6L);
        when(scaleRepo.countScopedRespondentsInWindow(eq("/EDGE/OPS"), eq(true), any(), any())).thenReturn(0L);

        EngagementSummaryDto dto = service.getEngagementSummary("TEAM", ou.getId(), false, 30, userId);

        assertThat(dto.masked()).isFalse();
        assertThat(dto.includeChildren()).isFalse();
        assertThat(dto.eligibleUsers()).isEqualTo(6);
        assertThat(dto.participationRate()).isEqualTo(100.0);
    }

    @Test
    void engagement_global_broadScopeNoNode_usesNullPathAndActiveUserCount() {
        UUID userId = UUID.randomUUID();
        when(orgUnitScopeService.hasBroadScope(anyCollection())).thenReturn(true);
        when(scaleRepo.countScopedRespondents(isNull(), eq(false), any())).thenReturn(50L);
        when(scaleRepo.findScopedOverallAverage(isNull(), eq(false), any())).thenReturn(Optional.of(3.9));
        when(scaleRepo.findScopedFormAverages(isNull(), eq(false), any())).thenReturn(List.of());
        when(scaleRepo.findScopedScoreDistribution(isNull(), eq(false), any())).thenReturn(List.of());
        when(userRepository.countByActiveTrue()).thenReturn(100L);
        when(scaleRepo.countScopedRespondentsInWindow(isNull(), eq(false), any(), any())).thenReturn(40L);
        when(scaleRepo.findScopedOverallAverageInWindow(isNull(), eq(false), any(), any()))
                .thenReturn(Optional.of(3.9));

        EngagementSummaryDto dto = service.getEngagementSummary(null, null, true, 30, userId);

        assertThat(dto.masked()).isFalse();
        assertThat(dto.scopeLevel()).isEqualTo("GLOBAL");
        assertThat(dto.nodeId()).isNull();
        assertThat(dto.eligibleUsers()).isEqualTo(100);
        assertThat(dto.participationRate()).isEqualTo(50.0);
        // equal current/prior → FLAT
        assertThat(dto.trend().direction()).isEqualTo("FLAT");
    }

    // -----------------------------------------------------------------------
    // Caller scope-bounding: narrow-scope caller requesting an out-of-scope node
    // -----------------------------------------------------------------------

    @Test
    void engagement_narrowScopeCaller_outOfScopeNode_fallsBackToOwnSubtree() {
        // Caller's own unit is /EDGE/OPS; they request a sibling /EDGE/HR they cannot see.
        OrganizationalUnit requested = orgUnit("/EDGE/HR");
        OrganizationalUnit ownUnit = orgUnit("/EDGE/OPS");
        User caller = userIn(ownUnit);

        when(orgUnitRepository.findById(requested.getId())).thenReturn(Optional.of(requested));
        when(orgUnitScopeService.hasBroadScope(anyCollection())).thenReturn(false);
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));
        // After falling back, the scope is the caller's own subtree path /EDGE/OPS.
        when(scaleRepo.countScopedRespondents(eq("/EDGE/OPS"), eq(false), any())).thenReturn(7L);
        when(scaleRepo.findScopedOverallAverage(eq("/EDGE/OPS"), eq(false), any())).thenReturn(Optional.of(3.5));
        when(scaleRepo.findScopedFormAverages(eq("/EDGE/OPS"), eq(false), any())).thenReturn(List.of());
        when(scaleRepo.findScopedScoreDistribution(eq("/EDGE/OPS"), eq(false), any())).thenReturn(List.of());
        when(userRepository.countByOrgUnitPathStartingWithAndActiveTrue("/EDGE/OPS")).thenReturn(7L);
        lenient().when(scaleRepo.countScopedRespondentsInWindow(eq("/EDGE/OPS"), eq(false), any(), any()))
                .thenReturn(0L);

        EngagementSummaryDto dto = service.getEngagementSummary("ENTITY", requested.getId(), true, 30, caller.getId());

        // The requested out-of-scope node is NOT echoed; resolved scope is the caller's own.
        assertThat(dto.masked()).isFalse();
        assertThat(dto.nodeId()).isNull();
        assertThat(dto.eligibleUsers()).isEqualTo(7);
    }
}
