package com.edge.pulse.services.psychometric;

import com.edge.pulse.data.dto.psychometric.CapabilityTrendDto;
import com.edge.pulse.data.dto.psychometric.CohortAnalyticsDto;
import com.edge.pulse.data.models.psychometric.CapabilityProfileCurrent;
import com.edge.pulse.data.models.psychometric.CapabilityScoreHistory;
import com.edge.pulse.data.models.psychometric.PsychometricScale;
import com.edge.pulse.data.models.psychometric.PsychometricTest;
import com.edge.pulse.repositories.psychometric.CapabilityProfileCurrentRepository;
import com.edge.pulse.repositories.psychometric.CapabilityScoreHistoryRepository;
import com.edge.pulse.repositories.psychometric.PsychometricScaleRepository;
import com.edge.pulse.repositories.psychometric.PsychometricTestRepository;
import com.edge.pulse.services.OrgUnitScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TalentAnalyticsServiceTest {

    @Mock CapabilityProfileCurrentRepository currentRepository;
    @Mock CapabilityScoreHistoryRepository historyRepository;
    @Mock PsychometricScaleRepository scaleRepository;
    @Mock PsychometricTestRepository testRepository;
    @Mock OrgUnitScopeService orgUnitScopeService;

    TalentAnalyticsService service;

    UUID testId, callerId, scaleA, scaleB;

    @BeforeEach
    void setUp() {
        service = new TalentAnalyticsService(currentRepository, historyRepository,
                scaleRepository, testRepository, orgUnitScopeService);
        testId = UUID.randomUUID();
        callerId = UUID.randomUUID();
        scaleA = UUID.randomUUID();
        scaleB = UUID.randomUUID();
        lenient().when(testRepository.existsById(testId)).thenReturn(true);
        lenient().when(orgUnitScopeService.resolveAccessibleOrgUnitIdsFromContext(callerId))
                .thenReturn(Set.of(UUID.randomUUID()));
    }

    private static CapabilityProfileCurrent row(UUID userId, UUID scaleId, double sten) {
        return CapabilityProfileCurrent.builder()
                .userId(userId).scaleId(scaleId).testId(UUID.randomUUID())
                .latestResultId(UUID.randomUUID())
                .stenScore(BigDecimal.valueOf(sten))
                .scoredAt(LocalDateTime.now()).build();
    }

    private PsychometricScale scale(UUID id, String name, boolean restricted) {
        PsychometricTest t = PsychometricTest.builder().id(testId).build();
        return PsychometricScale.builder().id(id).test(t).name(name).restricted(restricted).build();
    }

    @Test
    void cohortAnalytics_belowMinRespondents_isMasked() {
        // only 3 distinct subjects (< MIN_RESPONDENTS 5)
        List<CapabilityProfileCurrent> rows = List.of(
                row(UUID.randomUUID(), scaleA, 5),
                row(UUID.randomUUID(), scaleA, 6),
                row(UUID.randomUUID(), scaleA, 7));
        when(currentRepository.findCohort(eq(testId), anyList())).thenReturn(rows);

        CohortAnalyticsDto dto = service.cohortAnalytics(testId, callerId);

        assertThat(dto.masked()).isTrue();
        assertThat(dto.scales()).isEmpty();
        assertThat(dto.subjectCount()).isZero();
    }

    @Test
    void cohortAnalytics_buildsPerScaleHistogram_excludingThinScales() {
        // 6 distinct subjects; scale A has 6 stens, scale B only 2 → B suppressed
        UUID u1 = UUID.randomUUID(), u2 = UUID.randomUUID(), u3 = UUID.randomUUID();
        UUID u4 = UUID.randomUUID(), u5 = UUID.randomUUID(), u6 = UUID.randomUUID();
        List<CapabilityProfileCurrent> rows = List.of(
                row(u1, scaleA, 3), row(u2, scaleA, 5), row(u3, scaleA, 5),
                row(u4, scaleA, 7), row(u5, scaleA, 8), row(u6, scaleA, 6),
                row(u1, scaleB, 4), row(u2, scaleB, 5));
        when(currentRepository.findCohort(eq(testId), anyList())).thenReturn(rows);
        when(scaleRepository.findByTestId(testId)).thenReturn(List.of(
                scale(scaleA, "Resilience", false), scale(scaleB, "Drive", false)));

        CohortAnalyticsDto dto = service.cohortAnalytics(testId, callerId);

        assertThat(dto.masked()).isFalse();
        assertThat(dto.subjectCount()).isEqualTo(6);
        assertThat(dto.scales()).hasSize(1);
        CohortAnalyticsDto.ScaleCohortStat a = dto.scales().get(0);
        assertThat(a.scaleId()).isEqualTo(scaleA);
        assertThat(a.resultCount()).isEqualTo(6);
        assertThat(a.stenHistogram()).hasSize(10);
        // buckets: sten 3,5,5,7,8,6 → idx2=1, idx4=2, idx5=1, idx6=1, idx7=1
        assertThat(a.stenHistogram()[2]).isEqualTo(1);
        assertThat(a.stenHistogram()[4]).isEqualTo(2);
        assertThat(a.meanSten()).isEqualTo((3 + 5 + 5 + 7 + 8 + 6) / 6.0);
    }

    @Test
    void cohortAnalytics_noAccessibleScope_isMasked() {
        when(orgUnitScopeService.resolveAccessibleOrgUnitIdsFromContext(callerId))
                .thenReturn(Set.of());

        CohortAnalyticsDto dto = service.cohortAnalytics(testId, callerId);

        assertThat(dto.masked()).isTrue();
    }

    private static CapabilityScoreHistory hist(UUID scaleId, double sten, UUID normVersion,
                                               LocalDateTime at) {
        return CapabilityScoreHistory.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID()).scaleId(scaleId)
                .testId(UUID.randomUUID()).resultId(UUID.randomUUID())
                .stenScore(BigDecimal.valueOf(sten)).normTableVersionId(normVersion)
                .scoredAt(at).build();
    }

    @Test
    void capabilityTrend_ordersOldestToNewest_andFlagsNormBoundary() {
        UUID userId = UUID.randomUUID();
        UUID v1 = UUID.randomUUID(), v2 = UUID.randomUUID();
        LocalDateTime t0 = LocalDateTime.now().minusDays(60);
        List<CapabilityScoreHistory> series = List.of(
                hist(scaleA, 5, v1, t0),
                hist(scaleA, 6, v1, t0.plusDays(20)),
                hist(scaleA, 6.5, v2, t0.plusDays(40)));
        when(historyRepository.findByUserIdAndTestIdOrderByScoredAtAsc(userId, testId))
                .thenReturn(series);
        when(scaleRepository.findByTestId(testId)).thenReturn(List.of(scale(scaleA, "Resilience", false)));

        CapabilityTrendDto dto = service.capabilityTrend(userId, testId);

        assertThat(dto.scales()).hasSize(1);
        CapabilityTrendDto.ScaleTrend st = dto.scales().get(0);
        assertThat(st.nAdministrations()).isEqualTo(3);
        assertThat(st.normBoundaryCrossed()).isTrue();
        assertThat(st.points()).hasSize(3);
        assertThat(st.points().get(0).sten()).isEqualByComparingTo(new BigDecimal("5"));
        assertThat(st.points().get(2).sten()).isEqualByComparingTo(new BigDecimal("6.5"));
    }

    @Test
    void capabilityTrend_singleNorm_normBoundaryFalse() {
        UUID userId = UUID.randomUUID();
        UUID v1 = UUID.randomUUID();
        LocalDateTime t0 = LocalDateTime.now().minusDays(40);
        when(historyRepository.findByUserIdAndTestIdOrderByScoredAtAsc(userId, testId))
                .thenReturn(List.of(hist(scaleA, 5, v1, t0), hist(scaleA, 6, v1, t0.plusDays(20))));
        when(scaleRepository.findByTestId(testId)).thenReturn(List.of(scale(scaleA, "Resilience", false)));

        CapabilityTrendDto dto = service.capabilityTrend(userId, testId);

        assertThat(dto.scales().get(0).normBoundaryCrossed()).isFalse();
    }

    @Test
    void capabilityTrend_excludesRestrictedScale() {
        UUID userId = UUID.randomUUID();
        UUID v1 = UUID.randomUUID();
        LocalDateTime t0 = LocalDateTime.now().minusDays(10);
        when(historyRepository.findByUserIdAndTestIdOrderByScoredAtAsc(userId, testId))
                .thenReturn(List.of(hist(scaleA, 5, v1, t0), hist(scaleB, 9, v1, t0)));
        when(scaleRepository.findByTestId(testId)).thenReturn(List.of(
                scale(scaleA, "Resilience", false), scale(scaleB, "Manipulativeness", true)));

        CapabilityTrendDto dto = service.capabilityTrend(userId, testId);

        assertThat(dto.scales()).hasSize(1);
        assertThat(dto.scales().get(0).scaleId()).isEqualTo(scaleA);
    }

    @Test
    void cohortAnalytics_unknownTest_throws404() {
        UUID missing = UUID.randomUUID();
        when(testRepository.existsById(missing)).thenReturn(false);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.cohortAnalytics(missing, callerId))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
