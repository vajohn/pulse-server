package com.edge.pulse.services.psychometric;

import com.edge.pulse.data.dto.psychometric.CapabilityTrendDto;
import com.edge.pulse.data.dto.psychometric.CohortAnalyticsDto;
import com.edge.pulse.data.models.psychometric.CapabilityProfileCurrent;
import com.edge.pulse.data.models.psychometric.CapabilityScoreHistory;
import com.edge.pulse.data.models.psychometric.PsychometricScale;
import com.edge.pulse.repositories.psychometric.CapabilityProfileCurrentRepository;
import com.edge.pulse.repositories.psychometric.CapabilityScoreHistoryRepository;
import com.edge.pulse.repositories.OrganizationalUnitRepository;
import com.edge.pulse.repositories.psychometric.PsychometricScaleRepository;
import com.edge.pulse.repositories.psychometric.PsychometricTestRepository;
import com.edge.pulse.services.AnalyticsConstants;
import com.edge.pulse.services.OrgUnitScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Read boundary for Phase-4 talent analytics (§12). Cohort aggregation is org-scoped via
 * {@link OrgUnitScopeService} (four-branch ORG_WIDE → ENTITY → TEAM → own), masked below the
 * {@link AnalyticsConstants#MIN_RESPONDENTS} k-anonymity floor, and excludes restricted scales
 * (CWB/validity, D3) + INVALID results (D4 — those never produce a current/history row). The
 * per-employee trend returns the immutable history series and flags norm-version boundaries
 * without ever re-scoring (D5).
 */
@Service
@RequiredArgsConstructor
public class TalentAnalyticsService {

    private final CapabilityProfileCurrentRepository currentRepository;
    private final CapabilityScoreHistoryRepository historyRepository;
    private final PsychometricScaleRepository scaleRepository;
    private final PsychometricTestRepository testRepository;
    private final OrgUnitScopeService orgUnitScopeService;
    private final OrganizationalUnitRepository orgUnitRepository;

    /** Org-scoped cohort distribution per leaf scale (D2/D3/D4). */
    @Transactional(readOnly = true)
    public CohortAnalyticsDto cohortAnalytics(UUID testId, UUID callerId) {
        if (!testRepository.existsById(testId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        List<UUID> accessibleOrgUnitIds =
                new ArrayList<>(orgUnitScopeService.resolveAccessibleOrgUnitIdsFromContext(callerId));
        if (accessibleOrgUnitIds.isEmpty()) {
            return CohortAnalyticsDto.masked(testId);
        }

        List<CapabilityProfileCurrent> rows =
                currentRepository.findCohort(testId, accessibleOrgUnitIds);

        long distinctSubjects = rows.stream()
                .map(CapabilityProfileCurrent::getUserId).distinct().count();
        if (distinctSubjects < AnalyticsConstants.MIN_RESPONDENTS) {
            return CohortAnalyticsDto.masked(testId);
        }

        Map<UUID, String> scaleNames = scaleRepository.findByTestId(testId).stream()
                .collect(Collectors.toMap(PsychometricScale::getId, PsychometricScale::getName));

        List<CohortAnalyticsDto.ScaleCohortStat> stats = rows.stream()
                .filter(r -> r.getStenScore() != null)
                .collect(Collectors.groupingBy(CapabilityProfileCurrent::getScaleId))
                .entrySet().stream()
                .map(e -> buildScaleStat(e.getKey(), scaleNames.get(e.getKey()), e.getValue()))
                .filter(s -> s.resultCount() >= AnalyticsConstants.MIN_RESPONDENTS) // per-cell k-anon
                .sorted(Comparator.comparing(CohortAnalyticsDto.ScaleCohortStat::scaleName,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return new CohortAnalyticsDto(testId, false, (int) distinctSubjects, stats);
    }

    private CohortAnalyticsDto.ScaleCohortStat buildScaleStat(
            UUID scaleId, String name, List<CapabilityProfileCurrent> rows) {
        long[] hist = new long[10];
        double sum = 0;
        for (CapabilityProfileCurrent r : rows) {
            int bucket = Math.max(1, Math.min(10,
                    r.getStenScore().setScale(0, RoundingMode.HALF_UP).intValue()));
            hist[bucket - 1]++;
            sum += r.getStenScore().doubleValue();
        }
        int n = rows.size();
        return new CohortAnalyticsDto.ScaleCohortStat(
                scaleId, name, n, n > 0 ? sum / n : null, hist);
    }

    /**
     * Per-employee longitudinal trend per leaf scale (D1/D5).
     *
     * <p>Org-scope enforced (C1): the target {@code userId} must live within the caller's
     * accessible org units (same four-branch resolution the cohort path uses). A caller with
     * org-wide scope sees every active org unit, so any in-scope target is permitted; a target
     * outside the caller's set — or with no org unit — yields {@code 403 FORBIDDEN}. This blocks
     * pulling an arbitrary employee's longitudinal history by UUID.
     */
    @Transactional(readOnly = true)
    public CapabilityTrendDto capabilityTrend(UUID callerId, UUID userId, UUID testId) {
        if (!testRepository.existsById(testId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Set<UUID> accessibleOrgUnitIds =
                orgUnitScopeService.resolveAccessibleOrgUnitIdsFromContext(callerId);
        UUID targetOrgUnitId = orgUnitRepository.findOrgUnitIdByUserId(userId).orElse(null);
        if (targetOrgUnitId == null || !accessibleOrgUnitIds.contains(targetOrgUnitId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        List<CapabilityScoreHistory> history =
                historyRepository.findByUserIdAndTestIdOrderByScoredAtAsc(userId, testId);

        List<PsychometricScale> scales = scaleRepository.findByTestId(testId);
        Set<UUID> restricted = scales.stream()
                .filter(PsychometricScale::isRestricted)
                .map(PsychometricScale::getId).collect(Collectors.toSet());
        Map<UUID, String> scaleNames = scales.stream()
                .collect(Collectors.toMap(PsychometricScale::getId, PsychometricScale::getName));

        Map<UUID, List<CapabilityScoreHistory>> byScale = history.stream()
                .filter(h -> !restricted.contains(h.getScaleId()))
                .collect(Collectors.groupingBy(CapabilityScoreHistory::getScaleId,
                        LinkedHashMap::new, Collectors.toList()));

        List<CapabilityTrendDto.ScaleTrend> scaleTrends = byScale.entrySet().stream()
                .map(e -> {
                    List<CapabilityScoreHistory> series = e.getValue();
                    List<CapabilityTrendDto.TrendPoint> points = series.stream()
                            .map(h -> new CapabilityTrendDto.TrendPoint(
                                    h.getStenScore(), h.getTScore(),
                                    h.getNormTableVersionId(), h.getScoredAt()))
                            .toList();
                    boolean boundary = false;
                    for (int i = 1; i < series.size(); i++) {
                        if (!Objects.equals(series.get(i).getNormTableVersionId(),
                                series.get(i - 1).getNormTableVersionId())) {
                            boundary = true;
                            break;
                        }
                    }
                    return new CapabilityTrendDto.ScaleTrend(
                            e.getKey(), scaleNames.get(e.getKey()), series.size(), boundary, points);
                })
                .toList();

        return new CapabilityTrendDto(userId, testId, scaleTrends);
    }
}
