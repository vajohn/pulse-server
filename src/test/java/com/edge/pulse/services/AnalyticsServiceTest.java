package com.edge.pulse.services;

import com.edge.pulse.data.dto.AnalyticsSummaryDto;
import com.edge.pulse.data.dto.OrgUnitNodeDto;
import com.edge.pulse.data.dto.SurveyReportDto;
import com.edge.pulse.data.enums.OrgLevel;
import com.edge.pulse.data.enums.QuestionType;
import com.edge.pulse.data.models.OrganizationalUnit;
import com.edge.pulse.data.models.Question;
import com.edge.pulse.data.models.Role;
import com.edge.pulse.data.models.Form;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.FormAssignment;
import com.edge.pulse.repositories.AnalyticsMvRepository;
import com.edge.pulse.repositories.FormOrgSessionCountsMvRepository;
import com.edge.pulse.repositories.FormSessionCountsMvRepository;
import com.edge.pulse.repositories.OrganizationalUnitRepository;
import com.edge.pulse.repositories.QuestionChoiceMvRepository;
import com.edge.pulse.repositories.QuestionRatingMvRepository;
import com.edge.pulse.repositories.QuestionRepository;
import com.edge.pulse.repositories.QuestionScaleMvRepository;
import com.edge.pulse.repositories.ResponseSessionRepository;
import com.edge.pulse.repositories.FormAssignmentRepository;
import com.edge.pulse.repositories.FormRepository;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.repositories.answer.AnswerChoiceRepository;
import com.edge.pulse.repositories.answer.AnswerRatingRepository;
import com.edge.pulse.repositories.answer.AnswerScaleRepository;
import com.edge.pulse.repositories.answer.AnswerTextRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.within;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

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

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Role hrRole() {
        var r = new Role();
        r.setName("HR_FULL_CRUD");
        return r;
    }

    private Role managerRole() {
        var r = new Role();
        r.setName("MANAGER");
        return r;
    }

    private OrganizationalUnit orgUnit(String path) {
        return OrganizationalUnit.builder()
                .id(UUID.randomUUID())
                .orgUnitName("Test OU")
                .orgLevel(OrgLevel.TEAM)
                .path(path)
                .active(true)
                .depth(1)
                .children(Collections.emptyList())
                .build();
    }

    private User hrUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("hr@test.com")
                .azureAdId("az1")
                .roles(Set.of(hrRole()))
                .build();
    }

    private User managerUser(OrganizationalUnit ou) {
        return User.builder()
                .id(UUID.randomUUID())
                .email("mgr@test.com")
                .azureAdId("az2")
                .roles(Set.of(managerRole()))
                .orgUnit(ou)
                .build();
    }

    // -----------------------------------------------------------------------
    // getTeamAnalytics
    // -----------------------------------------------------------------------

    @Test
    void getTeamAnalytics_belowThreshold_returnsPrivacyShield() {
        when(sessionRepository.countAllCompleted()).thenReturn(3L);
        when(sessionRepository.countAllCompletedAnonymous()).thenReturn(2L);
        when(sessionRepository.countAllCompletedIdentified()).thenReturn(1L);

        AnalyticsSummaryDto result = service.getTeamAnalytics();

        assertThat(result.thresholdMet()).isFalse();
        assertThat(result.totalRespondents()).isEqualTo(3);
        assertThat(result.overallAverageScore()).isEqualTo(0.0);
        assertThat(result.averageByCategory()).isEmpty();
        assertThat(result.anonymousRespondents()).isEqualTo(2);
        assertThat(result.identifiedRespondents()).isEqualTo(1);
    }

    @Test
    void getTeamAnalytics_atThreshold_returnsData() {
        when(sessionRepository.countAllCompleted()).thenReturn(10L);
        when(sessionRepository.countAllCompletedAnonymous()).thenReturn(4L);
        when(sessionRepository.countAllCompletedIdentified()).thenReturn(6L);
        when(mvRepo.findGlobalAverage(null)).thenReturn(Optional.of(3.8));
        when(mvRepo.findSurveyAverages(AnalyticsConstants.MIN_RESPONDENTS, null))
                .thenReturn(List.<Object[]>of(new Object[]{"Survey A", 3.8, 10}));
        when(mvRepo.findOrgUnitScores(AnalyticsConstants.MIN_RESPONDENTS, null))
                .thenReturn(List.<Object[]>of(new Object[]{"Backend Team", 4.0, 6}));

        AnalyticsSummaryDto result = service.getTeamAnalytics();

        assertThat(result.thresholdMet()).isTrue();
        assertThat(result.totalRespondents()).isEqualTo(10);
        assertThat(result.overallAverageScore()).isEqualTo(3.8);
        assertThat(result.averageByCategory()).containsKey("Survey A");
        assertThat(result.anonymousRespondents()).isEqualTo(4);
        assertThat(result.identifiedRespondents()).isEqualTo(6);
        assertThat(result.orgUnitScores()).hasSize(1);
        assertThat(result.orgUnitScores().get(0).orgUnitName()).isEqualTo("Backend Team");
    }

    @Test
    void getTeamAnalytics_withDaysFilter_usesLiveScaleRepo() {
        // days > 0 → live JPQL queries (not materialized view)
        OrganizationalUnit ou = orgUnit("/root/ops");
        User hr = hrUser();
        when(userRepository.findById(hr.getId())).thenReturn(Optional.of(hr));
        when(orgUnitScopeService.hasBroadScope(anyCollection())).thenReturn(true);

        when(sessionRepository.countAllCompletedFiltered(any(), any())).thenReturn(8L);
        when(sessionRepository.countAllCompletedAnonymousFiltered(any(), any())).thenReturn(2L);
        when(sessionRepository.countAllCompletedIdentifiedFiltered(any(), any())).thenReturn(6L);
        when(scaleRepo.findGlobalAverageFiltered(any(), any())).thenReturn(Optional.of(4.1));
        when(scaleRepo.findSurveyAveragesFiltered(eq(AnalyticsConstants.MIN_RESPONDENTS), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"Survey B", 4.1, 8}));
        when(scaleRepo.findOrgUnitScoresFiltered(eq(AnalyticsConstants.MIN_RESPONDENTS), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"Ops Team", 4.1, 8}));

        AnalyticsSummaryDto result = service.getTeamAnalytics(null, hr.getId(), 30);

        assertThat(result.thresholdMet()).isTrue();
        assertThat(result.overallAverageScore()).isEqualTo(4.1);
        assertThat(result.averageByCategory()).containsKey("Survey B");
    }

    @Test
    void getTeamAnalytics_withPathFilterNoDays_usesView() {
        // days = 0 + manager with org unit → materialized view with pathFilter
        OrganizationalUnit ou = orgUnit("/root/mgr");
        User manager = managerUser(ou);
        when(userRepository.findById(manager.getId())).thenReturn(Optional.of(manager));
        when(orgUnitScopeService.hasBroadScope(anyCollection())).thenReturn(false);

        String expectedFilter = "/root/mgr";
        when(sessionRepository.countAllCompletedFiltered(eq(expectedFilter), any())).thenReturn(6L);
        when(mvRepo.findGlobalAverage(expectedFilter)).thenReturn(Optional.of(3.5));
        when(mvRepo.findSurveyAverages(AnalyticsConstants.MIN_RESPONDENTS, expectedFilter))
                .thenReturn(List.<Object[]>of(new Object[]{"Survey C", 3.5, 6}));
        when(mvRepo.findOrgUnitScores(AnalyticsConstants.MIN_RESPONDENTS, expectedFilter))
                .thenReturn(List.<Object[]>of(new Object[]{"Mgr Team", 3.5, 6}));

        AnalyticsSummaryDto result = service.getTeamAnalytics(null, manager.getId(), 0);

        assertThat(result.thresholdMet()).isTrue();
        assertThat(result.overallAverageScore()).isEqualTo(3.5);
        assertThat(result.averageByCategory()).containsKey("Survey C");
    }

    // -----------------------------------------------------------------------
    // getSurveyReport — global (no scoping)
    // -----------------------------------------------------------------------

    @Test
    void getSurveyReport_surveyNotFound_throws() {
        UUID sid = UUID.randomUUID();
        when(formRepository.findById(sid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSurveyReport(sid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Form not found");
    }

    @Test
    void getSurveyReport_global_countsAllCompletedSessions() {
        UUID sid = UUID.randomUUID();
        Form form = Form.builder().id(sid).title("My Survey").build();
        when(formRepository.findById(sid)).thenReturn(Optional.of(form));
        when(assignmentRepository.findByFormIdAndActiveTrue(sid)).thenReturn(List.of());
        when(sessionRepository.countCompletedByForm(sid)).thenReturn(12L);
        when(sessionRepository.countInProgressByForm(sid)).thenReturn(3L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, true)).thenReturn(5L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, false)).thenReturn(7L);
        when(questionRepository.findByFormIdOrderByDisplayOrderAsc(sid)).thenReturn(List.of());

        SurveyReportDto result = service.getSurveyReport(sid);

        assertThat(result.completedSessions()).isEqualTo(12L);
        assertThat(result.inProgressSessions()).isEqualTo(3L);
        assertThat(result.anonymousSessions()).isEqualTo(5L);
        assertThat(result.identifiedSessions()).isEqualTo(7L);
        assertThat(result.privacyThresholdMet()).isTrue();
    }

    @Test
    void getSurveyReport_global_belowThreshold() {
        UUID sid = UUID.randomUUID();
        Form form = Form.builder().id(sid).title("Tiny Survey").build();
        when(formRepository.findById(sid)).thenReturn(Optional.of(form));
        when(assignmentRepository.findByFormIdAndActiveTrue(sid)).thenReturn(List.of());
        when(sessionRepository.countCompletedByForm(sid)).thenReturn(3L);
        when(sessionRepository.countInProgressByForm(sid)).thenReturn(1L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, true)).thenReturn(2L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, false)).thenReturn(1L);
        when(questionRepository.findByFormIdOrderByDisplayOrderAsc(sid)).thenReturn(List.of());

        SurveyReportDto result = service.getSurveyReport(sid);

        assertThat(result.privacyThresholdMet()).isFalse();
        assertThat(result.completedSessions()).isEqualTo(3L);
    }

    // -----------------------------------------------------------------------
    // getSurveyReport — HR role, org unit filter
    // -----------------------------------------------------------------------

    @Test
    void getSurveyReport_hrWithOrgUnitFilter_scopesToPath() {
        UUID sid = UUID.randomUUID();
        Form form = Form.builder().id(sid).title("Survey").build();
        when(formRepository.findById(sid)).thenReturn(Optional.of(form));

        User hr = hrUser();
        UUID ouId = UUID.randomUUID();
        OrganizationalUnit ou = orgUnit("/root/child");
        ou.setId(ouId);

        when(userRepository.findById(hr.getId())).thenReturn(Optional.of(hr));
        when(orgUnitScopeService.hasBroadScope(anyCollection())).thenReturn(true);
        when(orgUnitRepository.findById(ouId)).thenReturn(Optional.of(ou));

        String expectedPrefix = "/root/child";
        when(assignmentRepository.findByFormIdAndActiveTrue(sid)).thenReturn(List.of());
        when(sessionRepository.countCompletedByFormAndPath(sid, expectedPrefix)).thenReturn(5L);
        when(sessionRepository.countInProgressByFormAndPath(sid, expectedPrefix)).thenReturn(1L);
        when(questionRepository.findByFormIdOrderByDisplayOrderAsc(sid)).thenReturn(List.of());

        SurveyReportDto result = service.getSurveyReport(sid, ouId, hr.getId(), false);

        assertThat(result.completedSessions()).isEqualTo(5L);
        // When path-scoped, anonymous = 0 and identified = completedSessions
        assertThat(result.anonymousSessions()).isEqualTo(0L);
        assertThat(result.identifiedSessions()).isEqualTo(5L);
    }

    @Test
    void getSurveyReport_hrWithNoOrgUnitFilter_usesGlobalCount() {
        UUID sid = UUID.randomUUID();
        Form form = Form.builder().id(sid).title("Survey").build();
        when(formRepository.findById(sid)).thenReturn(Optional.of(form));

        User hr = hrUser();
        when(userRepository.findById(hr.getId())).thenReturn(Optional.of(hr));
        when(orgUnitScopeService.hasBroadScope(anyCollection())).thenReturn(true);

        when(assignmentRepository.findByFormIdAndActiveTrue(sid)).thenReturn(List.of());
        when(sessionRepository.countCompletedByForm(sid)).thenReturn(15L);
        when(sessionRepository.countInProgressByForm(sid)).thenReturn(2L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, true)).thenReturn(6L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, false)).thenReturn(9L);
        when(questionRepository.findByFormIdOrderByDisplayOrderAsc(sid)).thenReturn(List.of());

        SurveyReportDto result = service.getSurveyReport(sid, null, hr.getId(), false);

        assertThat(result.completedSessions()).isEqualTo(15L);
        assertThat(result.anonymousSessions()).isEqualTo(6L);
        assertThat(result.identifiedSessions()).isEqualTo(9L);
    }

    // -----------------------------------------------------------------------
    // getSurveyReport — MANAGER role, always scoped
    // -----------------------------------------------------------------------

    @Test
    void getSurveyReport_manager_alwaysScopedToOwnOrgUnit() {
        UUID sid = UUID.randomUUID();
        Form form = Form.builder().id(sid).title("Survey").build();
        when(formRepository.findById(sid)).thenReturn(Optional.of(form));

        OrganizationalUnit ou = orgUnit("/root/mgr-team");
        User manager = managerUser(ou);

        when(userRepository.findById(manager.getId())).thenReturn(Optional.of(manager));
        when(orgUnitScopeService.hasBroadScope(anyCollection())).thenReturn(false);

        String expectedPrefix = "/root/mgr-team";
        when(assignmentRepository.findByFormIdAndActiveTrue(sid)).thenReturn(List.of());
        when(sessionRepository.countCompletedByFormAndPath(sid, expectedPrefix)).thenReturn(4L);
        when(sessionRepository.countInProgressByFormAndPath(sid, expectedPrefix)).thenReturn(1L);
        when(questionRepository.findByFormIdOrderByDisplayOrderAsc(sid)).thenReturn(List.of());

        // Pass an orgUnitId — manager's own scope should override it
        UUID someOtherOuId = UUID.randomUUID();
        SurveyReportDto result = service.getSurveyReport(sid, someOtherOuId, manager.getId(), false);

        assertThat(result.completedSessions()).isEqualTo(4L);
        assertThat(result.anonymousSessions()).isEqualTo(0L);
        assertThat(result.identifiedSessions()).isEqualTo(4L);
    }

    @Test
    void getSurveyReport_manager_noOrgUnit_usesGlobalCount() {
        UUID sid = UUID.randomUUID();
        Form form = Form.builder().id(sid).title("Survey").build();
        when(formRepository.findById(sid)).thenReturn(Optional.of(form));

        // Manager with no org unit assigned — falls back to global
        User manager = User.builder()
                .id(UUID.randomUUID()).email("m@test.com").azureAdId("az3")
                .roles(Set.of(managerRole()))
                .orgUnit(null)
                .build();

        when(userRepository.findById(manager.getId())).thenReturn(Optional.of(manager));
        when(orgUnitScopeService.hasBroadScope(anyCollection())).thenReturn(false);
        when(assignmentRepository.findByFormIdAndActiveTrue(sid)).thenReturn(List.of());
        when(sessionRepository.countCompletedByForm(sid)).thenReturn(5L);
        when(sessionRepository.countInProgressByForm(sid)).thenReturn(0L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, true)).thenReturn(2L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, false)).thenReturn(3L);
        when(questionRepository.findByFormIdOrderByDisplayOrderAsc(sid)).thenReturn(List.of());

        SurveyReportDto result = service.getSurveyReport(sid, null, manager.getId(), false);

        assertThat(result.completedSessions()).isEqualTo(5L);
        assertThat(result.anonymousSessions()).isEqualTo(2L);
        assertThat(result.identifiedSessions()).isEqualTo(3L);
    }

    // -----------------------------------------------------------------------
    // getVisibleOrgUnits — role-based scoping
    // -----------------------------------------------------------------------

    @Test
    void getVisibleOrgUnits_hrUser_returnsAllActiveUnits() {
        User hr = hrUser();
        when(userRepository.findById(hr.getId())).thenReturn(Optional.of(hr));
        when(orgUnitScopeService.hasBroadScope(anyCollection())).thenReturn(true);

        OrganizationalUnit active = orgUnit("/a");
        active.setActive(true);
        OrganizationalUnit inactive = orgUnit("/b");
        inactive.setActive(false);
        when(orgUnitRepository.findAll()).thenReturn(List.of(active, inactive));

        List<OrgUnitNodeDto> result = service.getVisibleOrgUnits(hr.getId());

        // Only active units returned
        assertThat(result).hasSize(1);
        assertThat(result.get(0).path()).isEqualTo("/a");
    }

    @Test
    void getVisibleOrgUnits_manager_returnsSubtreeOnly() {
        OrganizationalUnit managerOu = orgUnit("/root/mgr");
        User manager = managerUser(managerOu);

        when(userRepository.findById(manager.getId())).thenReturn(Optional.of(manager));
        when(orgUnitScopeService.hasBroadScope(anyCollection())).thenReturn(false);

        // Child org units under manager's path
        OrganizationalUnit child1 = orgUnit("/root/mgr/team-a");
        child1.setActive(true);
        OrganizationalUnit child2 = orgUnit("/root/mgr/team-b");
        child2.setActive(true);
        when(orgUnitRepository.findByPathPrefix("/root/mgr")).thenReturn(List.of(managerOu, child1, child2));

        List<OrgUnitNodeDto> result = service.getVisibleOrgUnits(manager.getId());

        assertThat(result).hasSize(3);
    }

    @Test
    void getVisibleOrgUnits_manager_noOrgUnit_returnsEmpty() {
        User manager = User.builder()
                .id(UUID.randomUUID()).email("m@test.com").azureAdId("az3")
                .roles(Set.of(managerRole()))
                .orgUnit(null)
                .build();

        when(userRepository.findById(manager.getId())).thenReturn(Optional.of(manager));
        when(orgUnitScopeService.hasBroadScope(anyCollection())).thenReturn(false);

        List<OrgUnitNodeDto> result = service.getVisibleOrgUnits(manager.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void getVisibleOrgUnits_userNotFound_throws() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getVisibleOrgUnits(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    // -----------------------------------------------------------------------
    // Question breakdown — privacyThresholdMet propagation
    // -----------------------------------------------------------------------

    @Test
    void getSurveyReport_questionBreakdown_thresholdNotMet_returnsEmptyData() {
        UUID sid = UUID.randomUUID();
        UUID qid = UUID.randomUUID();
        Form form = Form.builder().id(sid).title("Survey").build();
        Question q = Question.builder()
                .id(qid).body("Rate this").questionType(QuestionType.SCALE).displayOrder(0).build();

        when(formRepository.findById(sid)).thenReturn(Optional.of(form));
        when(assignmentRepository.findByFormIdAndActiveTrue(sid)).thenReturn(List.of());
        when(sessionRepository.countCompletedByForm(sid)).thenReturn(2L); // below threshold
        when(sessionRepository.countInProgressByForm(sid)).thenReturn(0L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, true)).thenReturn(1L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, false)).thenReturn(1L);
        when(questionRepository.findByFormIdOrderByDisplayOrderAsc(sid)).thenReturn(List.of(q));
        // Global scope — batch MV load is triggered; return empty maps so live fallback is used.
        when(questionScaleMvRepo.findDistributionsByQuestionIds(anyCollection())).thenReturn(Map.of());
        when(questionChoiceMvRepo.findDistributionsByQuestionIds(anyCollection())).thenReturn(Map.of());
        when(questionRatingMvRepo.findStatsByQuestionIds(anyCollection())).thenReturn(Map.of());
        when(scaleRepo.countByQuestionId(qid)).thenReturn(2L);

        SurveyReportDto result = service.getSurveyReport(sid);

        assertThat(result.privacyThresholdMet()).isFalse();
        assertThat(result.questionBreakdowns()).hasSize(1);
        assertThat(result.questionBreakdowns().get(0).privacyThresholdMet()).isFalse();
        assertThat(result.questionBreakdowns().get(0).averageScale()).isNull();
        assertThat(result.questionBreakdowns().get(0).scaleDistribution()).isEmpty();
    }

    @Test
    void getSurveyReport_questionBreakdown_scale_aboveThreshold_returnsAvgAndDistribution() {
        UUID sid = UUID.randomUUID();
        UUID qid = UUID.randomUUID();
        Form form = Form.builder().id(sid).title("Survey").build();
        Question q = Question.builder()
                .id(qid).body("Rate this").questionType(QuestionType.SCALE).displayOrder(0).build();

        when(formRepository.findById(sid)).thenReturn(Optional.of(form));
        when(assignmentRepository.findByFormIdAndActiveTrue(sid)).thenReturn(List.of());
        when(sessionRepository.countCompletedByForm(sid)).thenReturn(8L);
        when(sessionRepository.countInProgressByForm(sid)).thenReturn(1L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, true)).thenReturn(3L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, false)).thenReturn(5L);
        when(questionRepository.findByFormIdOrderByDisplayOrderAsc(sid)).thenReturn(List.of(q));
        // Global scope (pathPrefix == null) — batch MV load.
        // Distribution [4→5, 5→3] → responseCount = 8, avg = (4*5 + 5*3) / 8 = 4.375
        when(questionScaleMvRepo.findDistributionsByQuestionIds(anyCollection()))
                .thenReturn(Map.of(qid, List.of(new Object[]{4, 5L}, new Object[]{5, 3L})));
        when(questionChoiceMvRepo.findDistributionsByQuestionIds(anyCollection())).thenReturn(Map.of());
        when(questionRatingMvRepo.findStatsByQuestionIds(anyCollection())).thenReturn(Map.of());

        SurveyReportDto result = service.getSurveyReport(sid);

        var qr = result.questionBreakdowns().get(0);
        assertThat(qr.privacyThresholdMet()).isTrue();
        assertThat(qr.averageScale()).isEqualTo(4.375);
        assertThat(qr.scaleDistribution()).hasSize(2);
        assertThat(qr.scaleDistribution().get(0).score()).isEqualTo(4);
        assertThat(qr.scaleDistribution().get(0).count()).isEqualTo(5L);
    }

    @Test
    void getSurveyReport_questionBreakdown_choice_aboveThreshold_usesMvBatch() {
        UUID sid = UUID.randomUUID();
        UUID qid = UUID.randomUUID();
        Form form = Form.builder().id(sid).title("Choice Survey").build();
        Question q = Question.builder()
                .id(qid).body("Pick one").questionType(QuestionType.CHOICE).displayOrder(0).build();

        when(formRepository.findById(sid)).thenReturn(Optional.of(form));
        when(assignmentRepository.findByFormIdAndActiveTrue(sid)).thenReturn(List.of());
        when(sessionRepository.countCompletedByForm(sid)).thenReturn(10L);
        when(sessionRepository.countInProgressByForm(sid)).thenReturn(1L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, true)).thenReturn(4L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, false)).thenReturn(6L);
        when(questionRepository.findByFormIdOrderByDisplayOrderAsc(sid)).thenReturn(List.of(q));
        when(questionScaleMvRepo.findDistributionsByQuestionIds(anyCollection())).thenReturn(Map.of());
        // Batch MV: Option A chosen 7 times, Option B 3 times → total responseCount = 10
        when(questionChoiceMvRepo.findDistributionsByQuestionIds(anyCollection()))
                .thenReturn(Map.of(qid, List.of(
                        new Object[]{"Option A", 7L},
                        new Object[]{"Option B", 3L}
                )));
        when(questionRatingMvRepo.findStatsByQuestionIds(anyCollection())).thenReturn(Map.of());

        SurveyReportDto result = service.getSurveyReport(sid);

        var qr = result.questionBreakdowns().get(0);
        assertThat(qr.privacyThresholdMet()).isTrue();
        assertThat(qr.choiceDistribution()).hasSize(2);
        assertThat(qr.choiceDistribution().get(0).label()).isEqualTo("Option A");
        assertThat(qr.choiceDistribution().get(0).count()).isEqualTo(7L);
        assertThat(qr.choiceDistribution().get(0).percentage()).isCloseTo(70.0, within(0.01));
    }

    @Test
    void getSurveyReport_choiceOption_countExceedsResponseCount_percentageCappedAt100() {
        // Live-query path (no MV entry for qid): countByQuestionId returns 10 (distinct respondents),
        // but findDistributionByQuestionId returns option A with count 15 (multi-select re-submission).
        // Without the clamp this would yield 150% — must be capped to 100%.
        UUID sid = UUID.randomUUID();
        UUID qid = UUID.randomUUID();
        Form form = Form.builder().id(sid).title("Multi-select Survey").build();
        Question q = Question.builder()
                .id(qid).body("Pick all that apply").questionType(QuestionType.CHOICE).displayOrder(0).build();

        when(formRepository.findById(sid)).thenReturn(Optional.of(form));
        when(assignmentRepository.findByFormIdAndActiveTrue(sid)).thenReturn(List.of());
        when(sessionRepository.countCompletedByForm(sid)).thenReturn(10L);
        when(sessionRepository.countInProgressByForm(sid)).thenReturn(0L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, true)).thenReturn(5L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, false)).thenReturn(5L);
        when(questionRepository.findByFormIdOrderByDisplayOrderAsc(sid)).thenReturn(List.of(q));
        when(questionScaleMvRepo.findDistributionsByQuestionIds(anyCollection())).thenReturn(Map.of());
        // Empty MV map → live-query path used for both count and distribution
        when(questionChoiceMvRepo.findDistributionsByQuestionIds(anyCollection())).thenReturn(Map.of());
        when(questionRatingMvRepo.findStatsByQuestionIds(anyCollection())).thenReturn(Map.of());
        // Live-query: distinct-respondent count = 10, but option A appears 15 times (re-submission)
        when(choiceRepo.countByQuestionId(qid)).thenReturn(10L);
        when(choiceRepo.findDistributionByQuestionId(qid)).thenReturn(List.of(
                new Object[]{"Option A", 15L},
                new Object[]{"Option B", 3L}
        ));

        SurveyReportDto result = service.getSurveyReport(sid);

        var dist = result.questionBreakdowns().get(0).choiceDistribution();
        double pctA = dist.get(0).percentage();
        double pctB = dist.get(1).percentage();

        // Option A: raw 150% → clamped to 100%
        assertThat(pctA).isLessThanOrEqualTo(100.0);
        assertThat(pctA).isCloseTo(100.0, within(0.01));
        // Option B: 3/10 = 30% — well within range, unaffected
        assertThat(pctB).isCloseTo(30.0, within(0.01));
    }

    @Test
    void getSurveyReport_choiceOption_zeroResponseCount_percentageIsZero() {
        UUID sid = UUID.randomUUID();
        UUID qid = UUID.randomUUID();
        Form form = Form.builder().id(sid).title("Empty Survey").build();
        Question q = Question.builder()
                .id(qid).body("Pick one").questionType(QuestionType.CHOICE).displayOrder(0).build();

        when(formRepository.findById(sid)).thenReturn(Optional.of(form));
        when(assignmentRepository.findByFormIdAndActiveTrue(sid)).thenReturn(List.of());
        // Zero completed sessions → responseCount = 0
        when(sessionRepository.countCompletedByForm(sid)).thenReturn(0L);
        when(sessionRepository.countInProgressByForm(sid)).thenReturn(0L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, true)).thenReturn(0L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, false)).thenReturn(0L);
        when(questionRepository.findByFormIdOrderByDisplayOrderAsc(sid)).thenReturn(List.of(q));
        when(questionScaleMvRepo.findDistributionsByQuestionIds(anyCollection())).thenReturn(Map.of());
        when(questionChoiceMvRepo.findDistributionsByQuestionIds(anyCollection())).thenReturn(Map.of());
        when(questionRatingMvRepo.findStatsByQuestionIds(anyCollection())).thenReturn(Map.of());

        SurveyReportDto result = service.getSurveyReport(sid);

        // Privacy threshold not met (0 < threshold) → choiceDistribution is empty, no divide-by-zero
        assertThat(result.questionBreakdowns().get(0).privacyThresholdMet()).isFalse();
        assertThat(result.questionBreakdowns().get(0).choiceDistribution()).isEmpty();
    }

    @Test
    void getSurveyReport_questionBreakdown_rating_aboveThreshold_usesMvBatch() {
        UUID sid = UUID.randomUUID();
        UUID qid = UUID.randomUUID();
        Form form = Form.builder().id(sid).title("Rating Survey").build();
        Question q = Question.builder()
                .id(qid).body("Rate colleague").questionType(QuestionType.RATING).displayOrder(0).build();

        when(formRepository.findById(sid)).thenReturn(Optional.of(form));
        when(assignmentRepository.findByFormIdAndActiveTrue(sid)).thenReturn(List.of());
        when(sessionRepository.countCompletedByForm(sid)).thenReturn(8L);
        when(sessionRepository.countInProgressByForm(sid)).thenReturn(0L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, true)).thenReturn(3L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, false)).thenReturn(5L);
        when(questionRepository.findByFormIdOrderByDisplayOrderAsc(sid)).thenReturn(List.of(q));
        when(questionScaleMvRepo.findDistributionsByQuestionIds(anyCollection())).thenReturn(Map.of());
        when(questionChoiceMvRepo.findDistributionsByQuestionIds(anyCollection())).thenReturn(Map.of());
        // Batch MV: subject_label, avg_stars, total_response_count
        when(questionRatingMvRepo.findStatsByQuestionIds(anyCollection()))
                .thenReturn(Map.of(qid, List.of(
                        new Object[]{"Communication", 4.2, 8L},
                        new Object[]{"Leadership", 3.8, 8L}
                )));

        SurveyReportDto result = service.getSurveyReport(sid);

        var qr = result.questionBreakdowns().get(0);
        assertThat(qr.privacyThresholdMet()).isTrue();
        assertThat(qr.responseCount()).isEqualTo(8L);
        assertThat(qr.ratingBySubject()).hasSize(2);
        assertThat(qr.ratingBySubject().get(0).subject()).isEqualTo("Communication");
        assertThat(qr.ratingBySubject().get(0).averageStars()).isCloseTo(4.2, within(0.001));
        assertThat(qr.averageRating()).isCloseTo(4.0, within(0.001)); // mean of 4.2 and 3.8
    }

    // -----------------------------------------------------------------------
    // Assignment breakdown — org unit with includeChildren, MV batch path
    // -----------------------------------------------------------------------

    @Test
    void getSurveyReport_assignmentBreakdown_orgUnit_includeChildren_usesMvBatch() {
        UUID sid = UUID.randomUUID();
        Form form = Form.builder().id(sid).title("Survey").build();

        OrganizationalUnit ou = orgUnit("/root/team");
        ou.setId(UUID.randomUUID());

        FormAssignment sa = Mockito.mock(FormAssignment.class);
        when(sa.getId()).thenReturn(UUID.randomUUID());
        when(sa.getUser()).thenReturn(null);
        when(sa.getOrgUnit()).thenReturn(ou);
        when(sa.isIncludeChildren()).thenReturn(true);

        when(formRepository.findById(sid)).thenReturn(Optional.of(form));
        when(assignmentRepository.findByFormIdAndActiveTrue(sid)).thenReturn(List.of(sa));
        when(sessionRepository.countCompletedByForm(sid)).thenReturn(15L);
        when(sessionRepository.countInProgressByForm(sid)).thenReturn(3L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, true)).thenReturn(5L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, false)).thenReturn(10L);
        when(questionRepository.findByFormIdOrderByDisplayOrderAsc(sid)).thenReturn(List.of());
        when(userRepository.countByOrgUnitPathStartingWithAndActiveTrue("/root/team")).thenReturn(20L);
        // MV returns two rows under the org unit path and one outside — subtree sum = 10 + 5 = 15
        when(formOrgSessionCountsMvRepo.findCountsByFormId(sid)).thenReturn(Map.of(
                "/root/team",     new long[]{10L, 2L},
                "/root/team/sub", new long[]{ 5L, 1L},
                "/root/other",    new long[]{ 3L, 0L}  // excluded: does not start with /root/team
        ));

        SurveyReportDto result = service.getSurveyReport(sid);

        assertThat(result.assignmentBreakdowns()).hasSize(1);
        var bd = result.assignmentBreakdowns().get(0);
        assertThat(bd.eligibleUsers()).isEqualTo(20L);
        assertThat(bd.completedSessions()).isEqualTo(15L); // 10 + 5
        assertThat(bd.inProgressSessions()).isEqualTo(3L); // 2 + 1
        assertThat(bd.completionRate()).isCloseTo(75.0, within(0.01)); // 15/20 * 100
    }

    // -----------------------------------------------------------------------
    // Participation/completion rate correctness: numerator = distinct users
    // -----------------------------------------------------------------------

    /**
     * A user who completed a form twice produces 2 sessions. The completion rate
     * must use distinct respondent users as the numerator, not raw session count,
     * to prevent rates exceeding 100%.
     */
    @Test
    void getSurveyReport_completionRate_usesDistinctRespondentUsersNotSessions() {
        UUID sid = UUID.randomUUID();
        Form form = Form.builder().id(sid).title("Survey").build();
        when(formRepository.findById(sid)).thenReturn(Optional.of(form));

        // 2 sessions from a single user who completed twice.
        when(assignmentRepository.findByFormIdAndActiveTrue(sid)).thenReturn(List.of());
        when(sessionRepository.countCompletedByForm(sid)).thenReturn(2L);
        when(sessionRepository.countInProgressByForm(sid)).thenReturn(0L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, true)).thenReturn(0L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, false)).thenReturn(2L);
        // Only 1 distinct respondent user (same user completed twice).
        when(sessionRepository.countDistinctRespondentUsersByForm(sid)).thenReturn(1L);
        when(questionRepository.findByFormIdOrderByDisplayOrderAsc(sid)).thenReturn(List.of());

        // totalEligibleUsers = 0 (no assignments) → completionRate = 0 (denominator guard).
        // Use a manual assignment to have a non-zero eligible denominator.
        // Re-run with one individual user assignment for a meaningful rate assertion.
        SurveyReportDto result = service.getSurveyReport(sid);

        // With 0 eligible (no assignments), rate = 0.0 regardless.
        assertThat(result.completionRate()).isEqualTo(0.0);
        assertThat(result.completedSessions()).isEqualTo(2L); // raw session count preserved
        assertThat(result.identifiedSessions()).isEqualTo(2L);
    }

    /**
     * Given 1 eligible user, 2 completed sessions (user completed twice):
     * completionRate must be 100.0 (1 distinct user / 1 eligible), NOT 200%.
     */
    @Test
    void getSurveyReport_completionRate_cappedAt100_whenUserCompletedTwice() {
        UUID sid = UUID.randomUUID();
        Form form = Form.builder().id(sid).title("Survey").build();
        User assignedUser = User.builder().id(UUID.randomUUID()).email("u@test.com").azureAdId("az").build();

        FormAssignment sa = Mockito.mock(FormAssignment.class);
        when(sa.getUser()).thenReturn(assignedUser);

        when(formRepository.findById(sid)).thenReturn(Optional.of(form));
        when(assignmentRepository.findByFormIdAndActiveTrue(sid)).thenReturn(List.of(sa));
        when(sessionRepository.countCompletedByForm(sid)).thenReturn(2L);
        when(sessionRepository.countInProgressByForm(sid)).thenReturn(0L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, true)).thenReturn(0L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, false)).thenReturn(2L);
        // 1 distinct respondent user (completed twice).
        when(sessionRepository.countDistinctRespondentUsersByForm(sid)).thenReturn(1L);
        when(questionRepository.findByFormIdOrderByDisplayOrderAsc(sid)).thenReturn(List.of());
        when(formOrgSessionCountsMvRepo.findCountsByFormId(sid)).thenReturn(Map.of());
        // Per-user MV: 2 completed sessions, 0 in-progress.
        when(formSessionCountsMvRepo.findCounts(sid, assignedUser.getId())).thenReturn(new long[]{2L, 0L});

        SurveyReportDto result = service.getSurveyReport(sid);

        // totalEligibleUsers = 1 (one individual assignment).
        // distinctRespondentUsers = 1. completionRate = 1/1 * 100 = 100.0 (NOT 200%).
        assertThat(result.completionRate()).isEqualTo(100.0);
        assertThat(result.completionRate()).isLessThanOrEqualTo(100.0);
        // Raw completed session count is still preserved for display.
        assertThat(result.completedSessions()).isEqualTo(2L);
    }

    /**
     * The per-assignment completionRate is also capped at 100.0.
     * When a user has 3 completed sessions but eligible=1, rate must be 100, not 300.
     */
    @Test
    void getSurveyReport_assignmentBreakdown_rateIsCappedAt100() {
        UUID sid = UUID.randomUUID();
        Form form = Form.builder().id(sid).title("Survey").build();
        User assignedUser = User.builder().id(UUID.randomUUID()).email("u@test.com").azureAdId("az").build();

        FormAssignment sa = Mockito.mock(FormAssignment.class);
        when(sa.getId()).thenReturn(UUID.randomUUID());
        when(sa.getUser()).thenReturn(assignedUser);

        when(formRepository.findById(sid)).thenReturn(Optional.of(form));
        when(assignmentRepository.findByFormIdAndActiveTrue(sid)).thenReturn(List.of(sa));
        when(sessionRepository.countCompletedByForm(sid)).thenReturn(3L);
        when(sessionRepository.countInProgressByForm(sid)).thenReturn(0L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, true)).thenReturn(0L);
        when(sessionRepository.countCompletedByFormAndAnonymous(sid, false)).thenReturn(3L);
        when(sessionRepository.countDistinctRespondentUsersByForm(sid)).thenReturn(1L);
        when(questionRepository.findByFormIdOrderByDisplayOrderAsc(sid)).thenReturn(List.of());
        when(formOrgSessionCountsMvRepo.findCountsByFormId(sid)).thenReturn(Map.of());
        // Per-user MV: 3 completed sessions (user completed 3 times).
        when(formSessionCountsMvRepo.findCounts(sid, assignedUser.getId())).thenReturn(new long[]{3L, 0L});

        SurveyReportDto result = service.getSurveyReport(sid);

        assertThat(result.assignmentBreakdowns()).hasSize(1);
        var bd = result.assignmentBreakdowns().get(0);
        assertThat(bd.eligibleUsers()).isEqualTo(1L);
        assertThat(bd.completedSessions()).isEqualTo(3L); // raw sessions preserved
        // Rate = min(100.0, 3/1 * 100) = 100.0 (was 300% before fix).
        assertThat(bd.completionRate()).isEqualTo(100.0);
        assertThat(bd.completionRate()).isLessThanOrEqualTo(100.0);
    }
}
