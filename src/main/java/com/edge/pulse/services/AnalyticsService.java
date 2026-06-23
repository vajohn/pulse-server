package com.edge.pulse.services;

import com.edge.pulse.data.dto.AdminReportSummary;
import com.edge.pulse.data.dto.AnalyticsSummaryDto;
import com.edge.pulse.data.dto.AssignmentBreakdownDto;
import com.edge.pulse.data.dto.EngagementSummaryDto;
import com.edge.pulse.data.dto.OrgUnitNodeDto;
import com.edge.pulse.data.dto.QuestionReportDto;
import com.edge.pulse.data.dto.SurveyReportDto;
import com.edge.pulse.data.enums.QuestionType;
import com.edge.pulse.data.models.Form;
import com.edge.pulse.data.models.FormAssignment;
import com.edge.pulse.data.models.OrganizationalUnit;
import com.edge.pulse.data.models.Question;
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
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.edge.pulse.services.AnalyticsConstants.MIN_RESPONDENTS;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AnalyticsMvRepository mvRepo;
    private final FormSessionCountsMvRepository formSessionCountsMvRepo;
    private final FormOrgSessionCountsMvRepository formOrgSessionCountsMvRepo;
    private final QuestionScaleMvRepository questionScaleMvRepo;
    private final QuestionChoiceMvRepository questionChoiceMvRepo;
    private final QuestionRatingMvRepository questionRatingMvRepo;
    private final FormRepository formRepository;
    private final FormAssignmentRepository assignmentRepository;
    private final QuestionRepository questionRepository;
    private final ResponseSessionRepository sessionRepository;
    private final AnswerScaleRepository scaleRepo;
    private final AnswerChoiceRepository choiceRepo;
    private final AnswerRatingRepository ratingRepo;
    private final AnswerTextRepository textRepo;
    private final UserRepository userRepository;
    private final OrganizationalUnitRepository orgUnitRepository;
    private final OrgUnitScopeService orgUnitScopeService;
    private final RoleChangeService roleChangeService;

    // -----------------------------------------------------------------------
    // Team / Dashboard analytics
    // -----------------------------------------------------------------------

    /**
     * Scoped overload — applies org-unit path filtering and optional date-window filtering.
     * MANAGER: always scoped to their own org unit subtree.
     * HR: scoped to the selected org unit if provided, or global.
     * days: when > 0, only counts sessions completed within the last {@code days} days.
     */
    @Transactional(readOnly = true)
    public AnalyticsSummaryDto getTeamAnalytics(UUID orgUnitId, UUID userId, int days) {
        String pathPrefix = resolvePathPrefix(orgUnitId, userId);
        // Pass raw prefix (no "%" suffix) — queries handle exact + descendant matching via
        // "path = :pathFilter OR path LIKE CONCAT(:pathFilter, '/%')" to avoid the boundary
        // ambiguity where prefix "/EDGE/7001" would otherwise also match "/EDGE/70011".
        String pathFilter = pathPrefix;
        // Use a far-past epoch when no date filter is required to avoid null-type inference
        // issues in PostgreSQL (it cannot determine the type of a null timestamp bind param).
        LocalDateTime since = days > 0 ? LocalDateTime.now().minusDays(days) : LocalDateTime.of(2000, 1, 1, 0, 0);

        long totalRespondents = sessionRepository.countAllCompletedFiltered(pathFilter, since);
        boolean thresholdMet  = totalRespondents >= MIN_RESPONDENTS;

        long anonymousRespondents;
        long identifiedRespondents;
        if (pathFilter != null) {
            // Path-scoped: anonymous sessions have no user/orgUnit so they are always excluded.
            anonymousRespondents  = 0;
            identifiedRespondents = totalRespondents;
        } else {
            anonymousRespondents  = sessionRepository.countAllCompletedAnonymousFiltered(pathFilter, since);
            identifiedRespondents = sessionRepository.countAllCompletedIdentifiedFiltered(pathFilter, since);
        }

        if (!thresholdMet) {
            return new AnalyticsSummaryDto(
                    (int) totalRespondents, 0.0,
                    Map.of(), Map.of(), List.of(), false,
                    (int) anonymousRespondents, (int) identifiedRespondents);
        }

        // Use the materialized view when no date filter is applied (days <= 0 → epoch sentinel).
        // Fall back to live JPQL queries only when a date window is requested (days > 0),
        // because the view does not store date-sliced rows.
        final boolean useLiveQuery = days > 0;

        double overallAvg = useLiveQuery
                ? scaleRepo.findGlobalAverageFiltered(pathFilter, since).orElse(0.0)
                : mvRepo.findGlobalAverage(pathFilter).orElse(0.0);

        List<Object[]> surveyRows = useLiveQuery
                ? scaleRepo.findSurveyAveragesFiltered(MIN_RESPONDENTS, pathFilter, since)
                : mvRepo.findSurveyAverages(MIN_RESPONDENTS, pathFilter);
        Map<String, Double> avgByCategory = new LinkedHashMap<>();
        Map<String, Integer> respondentsByCategory = new LinkedHashMap<>();
        for (Object[] row : surveyRows) {
            String title = (String) row[0];
            double avg   = ((Number) row[1]).doubleValue();
            int count    = ((Number) row[2]).intValue();
            avgByCategory.put(title, avg);
            respondentsByCategory.put(title, count);
        }

        List<Object[]> orgRows = useLiveQuery
                ? scaleRepo.findOrgUnitScoresFiltered(MIN_RESPONDENTS, pathFilter, since)
                : mvRepo.findOrgUnitScores(MIN_RESPONDENTS, pathFilter);
        List<AnalyticsSummaryDto.OrgUnitScoreDto> orgUnitScores = orgRows.stream()
                .map(row -> new AnalyticsSummaryDto.OrgUnitScoreDto(
                        (String) row[0],
                        ((Number) row[1]).doubleValue(),
                        ((Number) row[2]).intValue()
                ))
                .toList();

        return new AnalyticsSummaryDto(
                (int) totalRespondents,
                overallAvg,
                avgByCategory,
                respondentsByCategory,
                orgUnitScores,
                true,
                (int) anonymousRespondents,
                (int) identifiedRespondents
        );
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryDto getTeamAnalytics() {
        long totalRespondents = sessionRepository.countAllCompleted();
        long anonymousRespondents = sessionRepository.countAllCompletedAnonymous();
        long identifiedRespondents = sessionRepository.countAllCompletedIdentified();
        boolean thresholdMet = totalRespondents >= MIN_RESPONDENTS;

        if (!thresholdMet) {
            return new AnalyticsSummaryDto(
                    (int) totalRespondents, 0.0,
                    Map.of(), Map.of(), List.of(), false,
                    (int) anonymousRespondents, (int) identifiedRespondents);
        }

        // Read from materialized view — no date filter, no path filter
        double overallAvg = mvRepo.findGlobalAverage(null).orElse(0.0);

        // Per-form averages and respondent counts (used as "category" in Flutter)
        List<Object[]> surveyRows = mvRepo.findSurveyAverages(MIN_RESPONDENTS, null);
        Map<String, Double> avgByCategory = new LinkedHashMap<>();
        Map<String, Integer> respondentsByCategory = new LinkedHashMap<>();
        for (Object[] row : surveyRows) {
            String title = (String) row[0];
            double avg   = ((Number) row[1]).doubleValue();
            int count    = ((Number) row[2]).intValue();
            avgByCategory.put(title, avg);
            respondentsByCategory.put(title, count);
        }

        // Org-unit leaderboard
        List<Object[]> orgRows = mvRepo.findOrgUnitScores(MIN_RESPONDENTS, null);
        List<AnalyticsSummaryDto.OrgUnitScoreDto> orgUnitScores = orgRows.stream()
                .map(row -> new AnalyticsSummaryDto.OrgUnitScoreDto(
                        (String) row[0],
                        ((Number) row[1]).doubleValue(),
                        ((Number) row[2]).intValue()
                ))
                .toList();

        return new AnalyticsSummaryDto(
                (int) totalRespondents,
                overallAvg,
                avgByCategory,
                respondentsByCategory,
                orgUnitScores,
                true,
                (int) anonymousRespondents,
                (int) identifiedRespondents
        );
    }

    // -----------------------------------------------------------------------
    // Org-wide engagement analytics (PULSE-WEB-4)
    // -----------------------------------------------------------------------

    /**
     * Org-wide engagement summary for the HR dashboard (WEB-5) and scope
     * switcher (WEB-6).
     *
     * <p>Scope resolution: when {@code nodeId} is supplied the metrics are
     * computed for that org unit (subtree when {@code includeChildren}, the unit
     * alone otherwise). The caller is bounded by their own SCOPE_* authority —
     * a node outside the caller's accessible set yields a masked result rather
     * than leaking out-of-scope data. When {@code nodeId} is null and the caller
     * has broad scope the view is global; a narrow-scope caller with no node is
     * pinned to their own org unit subtree.
     *
     * <p>Privacy: any resolved scope with fewer than {@link AnalyticsConstants#MIN_RESPONDENTS}
     * distinct completed respondents returns {@code masked=true} with no aggregates.
     *
     * @param scopeLevel echoed back to the client (GROUP|CLUSTER|ENTITY|ORG_UNIT|TEAM); informational
     * @param nodeId     org unit to scope to, or null
     * @param includeChildren include the node's descendants when true; the node alone when false
     * @param days       engagement window length in days (must be &gt; 0)
     */
    @Transactional(readOnly = true)
    public EngagementSummaryDto getEngagementSummary(String scopeLevel, UUID nodeId,
                                                     boolean includeChildren, int days,
                                                     UUID requestingUserId) {
        int periodDays = days > 0 ? days : 30;

        // Resolve the org node, bounded by the caller's scope.
        OrganizationalUnit node = resolveScopedNode(nodeId, requestingUserId);

        final String pathFilter;     // null = global
        final UUID resolvedNodeId;
        final String resolvedNodeName;
        if (node != null) {
            pathFilter       = buildPathPrefix(node);
            resolvedNodeId   = node.getId();
            resolvedNodeName = node.getOrgUnitName();
        } else {
            // No node resolved. Global only when caller has broad scope; otherwise
            // a narrow-scope caller is pinned to their own subtree (resolvePathPrefix).
            Collection<String> authorities = extractAuthoritiesFromContext();
            if (orgUnitScopeService.hasBroadScope(authorities)) {
                pathFilter = null;
            } else {
                String own = resolvePathPrefix(null, requestingUserId);
                if (own == null) {
                    // No org unit and no broad scope → nothing visible → masked.
                    return EngagementSummaryDto.masked(
                            normalizeScopeLevel(scopeLevel), null, null, includeChildren, periodDays);
                }
                pathFilter = own;
            }
            resolvedNodeId   = null;
            resolvedNodeName = null;
        }

        // includeChildren only narrows when a concrete node is scoped; global
        // (pathFilter == null) ignores it. exactOnly = scope to the node alone.
        boolean exactOnly = pathFilter != null && !includeChildren;

        LocalDateTime now      = LocalDateTime.now();
        LocalDateTime curStart = now.minusDays(periodDays);

        long respondents = scaleRepo.countScopedRespondents(pathFilter, exactOnly, curStart);

        // SERVER-SIDE privacy gate: suppress everything below the threshold.
        if (respondents < MIN_RESPONDENTS) {
            return EngagementSummaryDto.masked(
                    normalizeScopeLevel(scopeLevel), resolvedNodeId, resolvedNodeName,
                    includeChildren, periodDays);
        }

        double overall = scaleRepo.findScopedOverallAverage(pathFilter, exactOnly, curStart).orElse(0.0);

        // Per-form ("category") means. Suppress any form that is individually
        // below the respondent threshold so a small sub-cohort can't be isolated.
        List<EngagementSummaryDto.CategoryScore> categoryScores =
                scaleRepo.findScopedFormAverages(pathFilter, exactOnly, curStart).stream()
                        .filter(r -> ((Number) r[2]).intValue() >= MIN_RESPONDENTS)
                        .map(r -> new EngagementSummaryDto.CategoryScore(
                                (String) r[0],
                                ((Number) r[1]).doubleValue(),
                                ((Number) r[2]).intValue()))
                        .toList();

        List<EngagementSummaryDto.ScoreBucket> distribution =
                scaleRepo.findScopedScoreDistribution(pathFilter, exactOnly, curStart).stream()
                        .map(r -> new EngagementSummaryDto.ScoreBucket(
                                ((Number) r[0]).intValue(),
                                ((Number) r[1]).longValue()))
                        .toList();

        // Eligible users = active users in the resolved scope (participation denominator).
        long eligibleUsers;
        if (pathFilter == null) {
            eligibleUsers = userRepository.countByActiveTrue();
        } else if (exactOnly && node != null) {
            eligibleUsers = userRepository.countByOrgUnitIdAndActiveTrue(node.getId());
        } else {
            eligibleUsers = userRepository.countByOrgUnitPathStartingWithAndActiveTrue(pathFilter);
        }
        double participationRate = eligibleUsers > 0
                ? (double) respondents / eligibleUsers * 100.0
                : 0.0;

        // Trend vs the immediately-preceding window of the same length.
        LocalDateTime prevStart = curStart.minusDays(periodDays);
        long prevRespondents = scaleRepo.countScopedRespondentsInWindow(
                pathFilter, exactOnly, prevStart, curStart);
        EngagementSummaryDto.Trend trend;
        if (prevRespondents >= MIN_RESPONDENTS) {
            double prev  = scaleRepo.findScopedOverallAverageInWindow(
                    pathFilter, exactOnly, prevStart, curStart).orElse(0.0);
            double delta = overall - prev;
            String dir = Math.abs(delta) < 1e-9 ? "FLAT" : (delta > 0 ? "UP" : "DOWN");
            trend = new EngagementSummaryDto.Trend(overall, prev, delta, dir);
        } else {
            // Not enough prior-period respondents to compare without leaking a tiny cohort.
            trend = new EngagementSummaryDto.Trend(overall, null, null, "NO_PRIOR_DATA");
        }

        return new EngagementSummaryDto(
                normalizeScopeLevel(scopeLevel),
                resolvedNodeId, resolvedNodeName, includeChildren, periodDays,
                false,
                (int) respondents,
                (int) eligibleUsers,
                participationRate,
                overall,
                categoryScores,
                distribution,
                trend,
                null /* eNPS unsupported by data model — see report */);
    }

    private String normalizeScopeLevel(String scopeLevel) {
        return (scopeLevel == null || scopeLevel.isBlank()) ? "GLOBAL" : scopeLevel;
    }

    /**
     * Resolves the requested org node, bounded by the caller's scope.
     * Returns null when no node was requested. Returns null (→ caller falls back
     * to their own-scope path or global) when the requested node is outside the
     * caller's accessible set, so out-of-scope data is never surfaced.
     */
    private OrganizationalUnit resolveScopedNode(UUID nodeId, UUID requestingUserId) {
        if (nodeId == null || requestingUserId == null) return null;
        OrganizationalUnit node = orgUnitRepository.findById(nodeId).orElse(null);
        if (node == null) return null;

        Collection<String> authorities = extractAuthoritiesFromContext();
        if (orgUnitScopeService.hasBroadScope(authorities)) {
            return node;
        }
        // Narrow scope: only allow nodes inside the caller's own subtree.
        String ownPrefix = resolvePathPrefix(null, requestingUserId);
        if (ownPrefix != null && matchesPathScope(node.getPath(), ownPrefix)) {
            return node;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Per-form detailed report
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public SurveyReportDto getSurveyReport(UUID formId, UUID orgUnitId, UUID requestingUserId, boolean canViewText) {
        Form form = formRepository.findById(formId)
                .orElseThrow(() -> new IllegalArgumentException("Form not found: " + formId));

        // Resolve path-based scope
        String pathPrefix = resolvePathPrefix(orgUnitId, requestingUserId);

        // Fetch active assignments (used for both eligible-user count and breakdowns)
        List<FormAssignment> assignments = assignmentRepository.findByFormIdAndActiveTrue(formId);

        long completedSessions;
        long inProgressSessions;

        if (pathPrefix != null) {
            completedSessions  = sessionRepository.countCompletedByFormAndPath(formId, pathPrefix);
            inProgressSessions = sessionRepository.countInProgressByFormAndPath(formId, pathPrefix);
        } else {
            completedSessions  = sessionRepository.countCompletedByForm(formId);
            inProgressSessions = sessionRepository.countInProgressByForm(formId);
        }

        // Compute eligible user count from assignments (Issue 1 fix)
        long totalEligibleUsers = assignments.stream().mapToLong(sa -> {
            if (sa.getUser() != null) return 1L;
            if (sa.getOrgUnit() == null) return 0L;
            return sa.isIncludeChildren()
                    ? userRepository.countByOrgUnitPathStartingWithAndActiveTrue(sa.getOrgUnit().getPath())
                    : userRepository.countByOrgUnitIdAndActiveTrue(sa.getOrgUnit().getId());
        }).sum();

        // Anonymous sessions have no user/orgUnit, so they can't appear in a path-scoped count.
        long anonymousSessions;
        long identifiedSessions;
        if (pathPrefix != null) {
            anonymousSessions  = 0;
            identifiedSessions = completedSessions;
        } else {
            anonymousSessions  = sessionRepository.countCompletedByFormAndAnonymous(formId, true);
            identifiedSessions = sessionRepository.countCompletedByFormAndAnonymous(formId, false);
        }

        // Completion rate based on eligible users (identified sessions / eligible users)
        double completionRate = totalEligibleUsers > 0
                ? (double) identifiedSessions / totalEligibleUsers * 100.0
                : 0.0;

        boolean surveyThresholdMet = completedSessions >= MIN_RESPONDENTS;

        List<Question> questions = questionRepository.findByFormIdOrderByDisplayOrderAsc(formId);
        List<java.util.UUID> questionIds = questions.stream().map(Question::getId).toList();

        // Batch-load all question analytics from MVs when in global (unscoped) view.
        // Replaces O(N) per-question live aggregate queries with 3 batch MV reads.
        // When pathPrefix != null (manager/scoped HR), MVs cannot encode arbitrary path
        // prefixes, so these maps are left empty and live queries are used per question.
        final Map<UUID, List<Object[]>> scaleDistMap;
        final Map<UUID, List<Object[]>> choiceDistMap;
        final Map<UUID, List<Object[]>> ratingStatsMap;
        if (pathPrefix == null && !questionIds.isEmpty()) {
            scaleDistMap   = questionScaleMvRepo.findDistributionsByQuestionIds(questionIds);
            choiceDistMap  = questionChoiceMvRepo.findDistributionsByQuestionIds(questionIds);
            ratingStatsMap = questionRatingMvRepo.findStatsByQuestionIds(questionIds);
        } else {
            scaleDistMap   = Map.of();
            choiceDistMap  = Map.of();
            ratingStatsMap = Map.of();
        }

        List<QuestionReportDto> breakdowns = questions.stream()
                .map(q -> buildQuestionReport(q, surveyThresholdMet, pathPrefix, canViewText,
                                              scaleDistMap, choiceDistMap, ratingStatsMap))
                .toList();

        // Pre-load all org-path session counts for this form in one MV query.
        // buildAssignmentBreakdown computes subtree totals in-memory via path prefix matching.
        Map<String, long[]> orgSessionCounts = formOrgSessionCountsMvRepo.findCountsByFormId(formId);

        // Build per-assignment breakdown
        List<AssignmentBreakdownDto> assignmentBreakdowns = assignments.stream()
                .map(sa -> buildAssignmentBreakdown(sa, formId, orgSessionCounts))
                .toList();

        return new SurveyReportDto(
                formId,
                form.getTitle(),
                assignments.size(),
                totalEligibleUsers,
                completedSessions,
                inProgressSessions,
                completionRate,
                surveyThresholdMet,
                breakdowns,
                anonymousSessions,
                identifiedSessions,
                assignmentBreakdowns
        );
    }

    /** Convenience overload for callers that don't scope by org unit or need text. */
    @Transactional(readOnly = true)
    public SurveyReportDto getSurveyReport(UUID formId) {
        return getSurveyReport(formId, null, null, false);
    }

    // -----------------------------------------------------------------------
    // Per-assignment breakdown helper (Issue 2)
    // -----------------------------------------------------------------------

    /**
     * Builds the per-assignment breakdown row.
     *
     * @param orgSessionCounts pre-loaded map from {@code mv_form_org_session_counts} for this
     *                         form: org_path → long[]{completedCount, inProgressCount}.
     *                         Subtree totals are derived in-memory by matching paths that
     *                         start with the org unit's path prefix.
     */
    private AssignmentBreakdownDto buildAssignmentBreakdown(FormAssignment sa, UUID formId,
                                                             Map<String, long[]> orgSessionCounts) {
        long eligible;
        long completed;
        long inProgress;
        String ouId = null;
        String ouName;

        if (sa.getUser() != null) {
            eligible = 1L;
            // Use per-user MV to avoid COUNT queries; fall back to live when row absent.
            long[] mvCounts = formSessionCountsMvRepo.findCounts(formId, sa.getUser().getId());
            if (mvCounts != null) {
                completed  = mvCounts[0];
                inProgress = mvCounts[1];
            } else {
                completed  = sessionRepository.countCompletedByFormAndUserId(formId, sa.getUser().getId());
                inProgress = sessionRepository.countInProgressByFormAndUserId(formId, sa.getUser().getId());
            }
            ouName = sa.getUser().getEmail();
        } else if (sa.getOrgUnit() != null) {
            String pathPrefix = buildPathPrefix(sa.getOrgUnit());
            eligible = sa.isIncludeChildren()
                    ? userRepository.countByOrgUnitPathStartingWithAndActiveTrue(sa.getOrgUnit().getPath())
                    : userRepository.countByOrgUnitIdAndActiveTrue(sa.getOrgUnit().getId());

            if (sa.isIncludeChildren()) {
                // Subtree: sum rows whose org_path equals the prefix (own unit) OR starts
                // with prefix + "/" (descendants). This avoids the boundary ambiguity where
                // startsWith("/EDGE/7001") would also match "/EDGE/70011" (a sibling).
                completed  = orgSessionCounts.entrySet().stream()
                        .filter(e -> matchesPathScope(e.getKey(), pathPrefix))
                        .mapToLong(e -> e.getValue()[0]).sum();
                inProgress = orgSessionCounts.entrySet().stream()
                        .filter(e -> matchesPathScope(e.getKey(), pathPrefix))
                        .mapToLong(e -> e.getValue()[1]).sum();
            } else {
                // Exact org unit only: look up by the unit's own path.
                long[] counts = orgSessionCounts.getOrDefault(
                        sa.getOrgUnit().getPath(), new long[]{0L, 0L});
                completed  = counts[0];
                inProgress = counts[1];
            }
            ouId   = sa.getOrgUnit().getId().toString();
            ouName = sa.getOrgUnit().getOrgUnitName();
        } else {
            eligible   = 0L;
            completed  = 0L;
            inProgress = 0L;
            ouName     = "Unknown";
        }

        double rate = eligible > 0 ? (double) completed / eligible * 100.0 : 0.0;
        return new AssignmentBreakdownDto(
                sa.getId(), ouName, ouId,
                eligible, completed, inProgress,
                rate, completed >= MIN_RESPONDENTS
        );
    }

    // -----------------------------------------------------------------------
    // Scope resolution helpers
    // -----------------------------------------------------------------------

    /**
     * Returns the path prefix to use for filtering, or null for no filter.
     * MANAGER: forced to their own org unit's path subtree.
     * HR + orgUnitId provided: uses the selected org unit's subtree.
     * HR + no orgUnitId: no filter (global).
     */
    private String resolvePathPrefix(UUID orgUnitId, UUID requestingUserId) {
        if (requestingUserId == null) return null;
        User user = userRepository.findById(requestingUserId).orElse(null);
        if (user == null) return null;

        Collection<String> authorities = extractAuthoritiesFromContext();
        if (orgUnitScopeService.hasBroadScope(authorities)) {
            // SCOPE_ORG_WIDE / SCOPE_ENTITY: optional org unit filter; otherwise no restriction
            if (orgUnitId != null) {
                OrganizationalUnit ou = orgUnitRepository.findById(orgUnitId).orElse(null);
                if (ou != null) return buildPathPrefix(ou);
            }
            return null;
        }

        // SCOPE_TEAM or no scope: restrict to user's own org unit subtree
        if (user.getOrgUnit() == null) return null;
        return buildPathPrefix(user.getOrgUnit());
    }

    /**
     * Returns the path prefix used for org unit subtree queries.
     * The {@code path} column on every org unit is its full inclusive path
     * (e.g. "/EDGE/OPS" contains both EDGE and OPS segments), so the path
     * itself is the correct prefix — children naturally start with it.
     */
    private String buildPathPrefix(OrganizationalUnit ou) {
        return ou.getPath().isEmpty() ? "/" + ou.getId() : ou.getPath();
    }

    /**
     * Returns {@code true} when {@code candidatePath} is exactly {@code scopePath}
     * (the org unit itself) or is a proper descendant (starts with {@code scopePath + "/"}).
     *
     * <p>Using plain {@code startsWith(scopePath)} would incorrectly match sibling paths
     * that share a string prefix (e.g. {@code /EDGE/7001} would match {@code /EDGE/70011}).
     * This method enforces a path-boundary check instead.
     */
    private boolean matchesPathScope(String candidatePath, String scopePath) {
        return candidatePath.equals(scopePath) || candidatePath.startsWith(scopePath + "/");
    }

    // -----------------------------------------------------------------------
    // Visible org units for the filter panel
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<OrgUnitNodeDto> getVisibleOrgUnits(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        List<OrganizationalUnit> units;
        Collection<String> authorities = extractAuthoritiesFromContext();
        if (orgUnitScopeService.hasBroadScope(authorities)) {
            units = orgUnitRepository.findAll();
        } else {
            if (user.getOrgUnit() == null) return List.of();
            String pathPrefix = buildPathPrefix(user.getOrgUnit());
            units = orgUnitRepository.findByPathPrefix(pathPrefix);
            // Include the user's own org unit
            if (units.stream().noneMatch(u -> u.getId().equals(user.getOrgUnit().getId()))) {
                units = new java.util.ArrayList<>(units);
                units.add(0, user.getOrgUnit());
            }
        }

        return units.stream()
                .filter(OrganizationalUnit::isActive)
                .map(ou -> new OrgUnitNodeDto(
                        ou.getId(),
                        ou.getOrgUnitName(),
                        ou.getOrgLevel(),
                        ou.getParent() != null ? ou.getParent().getId() : null,
                        ou.getDepth(),
                        ou.getPath()
                ))
                .toList();
    }

    // -----------------------------------------------------------------------
    // Admin report summary (AdminReportController)
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public AdminReportSummary getAdminReportSummary() {
        long totalUsers        = userRepository.count();
        long activeUsers       = userRepository.countByActiveTrue();
        long totalForms        = formRepository.count();
        long totalAssignments  = assignmentRepository.count();
        long completedSessions = sessionRepository.countByCompletedAtIsNotNull();
        long pendingApprovals  = roleChangeService.getPendingCount();
        return new AdminReportSummary(totalUsers, activeUsers, totalForms,
                totalAssignments, completedSessions, pendingApprovals);
    }

    // -----------------------------------------------------------------------
    // Per-question breakdown
    // -----------------------------------------------------------------------

    /**
     * Builds the per-question analytics breakdown.
     *
     * <p>When {@code pathPrefix} is {@code null} (global HR view), the pre-loaded MV maps are
     * used to derive both the response count and distribution data without any live queries.
     * When {@code pathPrefix} is set (manager/scoped HR), the maps are empty and live JPQL
     * queries are used — MVs cannot encode arbitrary org-unit path prefixes.
     */
    private QuestionReportDto buildQuestionReport(Question question, boolean surveyThresholdMet,
                                                   String pathPrefix, boolean canViewText,
                                                   Map<UUID, List<Object[]>> scaleDistMap,
                                                   Map<UUID, List<Object[]>> choiceDistMap,
                                                   Map<UUID, List<Object[]>> ratingStatsMap) {
        UUID qid = question.getId();
        QuestionType type = question.getQuestionType();

        // Derive response count from pre-loaded MV data when available (global view).
        // For scoped views (pathPrefix != null) the maps are empty → fall back to live queries.
        long responseCount = switch (type) {
            case SCALE -> {
                List<Object[]> mvRows = scaleDistMap.get(qid);
                yield (mvRows != null)
                        ? mvRows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum()
                        : (pathPrefix != null
                                ? scaleRepo.countByQuestionIdAndPath(qid, pathPrefix)
                                : scaleRepo.countByQuestionId(qid));
            }
            case CHOICE, CHOICE_SINGLE -> {
                List<Object[]> mvRows = choiceDistMap.get(qid);
                yield (mvRows != null)
                        ? mvRows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum()
                        : (pathPrefix != null
                                ? choiceRepo.countByQuestionIdAndPath(qid, pathPrefix)
                                : choiceRepo.countByQuestionId(qid));
            }
            case RATING, MULTI_RATING -> {
                List<Object[]> mvRows = ratingStatsMap.get(qid);
                // total_response_count is stored in index 2 of each row (same value per question).
                yield (mvRows != null && !mvRows.isEmpty())
                        ? ((Number) mvRows.get(0)[2]).longValue()
                        : (pathPrefix != null
                                ? ratingRepo.countResponsesByQuestionIdAndPath(qid, pathPrefix)
                                : ratingRepo.countResponsesByQuestionId(qid));
            }
            case TEXT -> pathPrefix != null
                    ? textRepo.countByQuestionIdAndPath(qid, pathPrefix)
                    : textRepo.countByQuestionId(qid);
            // Phase-2 psychometric types are not reported via survey analytics
            case CHOICE_MULTIPLE, ADJECTIVE_CHECKLIST, FORCED_CHOICE -> 0L;
        };

        boolean qThresholdMet = surveyThresholdMet && responseCount >= MIN_RESPONDENTS;

        return switch (type) {
            case SCALE        -> buildScaleReport(question, responseCount, qThresholdMet,
                                                  pathPrefix, scaleDistMap);
            case CHOICE, CHOICE_SINGLE -> buildChoiceReport(question, responseCount, qThresholdMet,
                                                             pathPrefix, choiceDistMap);
            case RATING, MULTI_RATING  -> buildRatingReport(question, responseCount, qThresholdMet,
                                                             pathPrefix, ratingStatsMap);
            case TEXT         -> buildTextReport(question, responseCount, qThresholdMet,
                                                 canViewText, pathPrefix);
            // Phase-2 psychometric types are not reported via survey analytics
            case CHOICE_MULTIPLE, ADJECTIVE_CHECKLIST, FORCED_CHOICE ->
                buildChoiceReport(question, responseCount, qThresholdMet, pathPrefix, choiceDistMap);
        };
    }

    private QuestionReportDto buildScaleReport(Question q, long responseCount, boolean thresholdMet,
                                               String pathPrefix, Map<UUID, List<Object[]>> scaleDistMap) {
        Double avg = null;
        List<QuestionReportDto.ScoreEntry> dist = List.of();

        if (thresholdMet) {
            List<Object[]> mvRows = scaleDistMap.get(q.getId());
            if (mvRows != null) {
                // Global view: distribution already loaded from MV batch read.
                long totalCount = mvRows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();
                if (totalCount > 0) {
                    avg = mvRows.stream()
                            .mapToDouble(r -> (double) ((Number) r[0]).intValue() * ((Number) r[1]).longValue())
                            .sum() / totalCount;
                }
                dist = mvRows.stream()
                        .map(row -> new QuestionReportDto.ScoreEntry(
                                ((Number) row[0]).intValue(),
                                ((Number) row[1]).longValue()))
                        .toList();
            } else {
                // Scoped view: live JPQL queries (MV cannot encode arbitrary path prefixes).
                avg = scaleRepo.findAverageByQuestionIdAndPath(q.getId(), pathPrefix).orElse(null);
                List<Object[]> rows = scaleRepo.findDistributionByQuestionIdAndPath(q.getId(), pathPrefix);
                dist = rows.stream()
                        .map(row -> new QuestionReportDto.ScoreEntry(
                                ((Number) row[0]).intValue(),
                                ((Number) row[1]).longValue()))
                        .toList();
            }
        }

        return new QuestionReportDto(
                q.getId(), q.getBody(), q.getQuestionType(), q.getDisplayOrder(),
                responseCount, thresholdMet,
                avg, dist,
                null, List.of(),
                List.of(),
                null, null
        );
    }

    private QuestionReportDto buildChoiceReport(Question q, long responseCount, boolean thresholdMet,
                                                String pathPrefix, Map<UUID, List<Object[]>> choiceDistMap) {
        List<QuestionReportDto.ChoiceEntry> dist = List.of();

        if (thresholdMet) {
            List<Object[]> mvRows = choiceDistMap.get(q.getId());
            List<Object[]> rows = (mvRows != null)
                    ? mvRows   // global view: already loaded from MV batch read
                    : (pathPrefix != null
                            ? choiceRepo.findDistributionByQuestionIdAndPath(q.getId(), pathPrefix)
                            : choiceRepo.findDistributionByQuestionId(q.getId()));
            dist = rows.stream()
                    .map(row -> new QuestionReportDto.ChoiceEntry(
                            (String) row[0],
                            ((Number) row[1]).longValue(),
                            responseCount > 0
                                    ? ((Number) row[1]).doubleValue() / responseCount * 100.0
                                    : 0.0))
                    .toList();
        }

        return new QuestionReportDto(
                q.getId(), q.getBody(), q.getQuestionType(), q.getDisplayOrder(),
                responseCount, thresholdMet,
                null, List.of(),
                null, List.of(),
                dist,
                null, null
        );
    }

    private QuestionReportDto buildRatingReport(Question q, long responseCount, boolean thresholdMet,
                                                String pathPrefix, Map<UUID, List<Object[]>> ratingStatsMap) {
        Double avg = null;
        List<QuestionReportDto.RatingSubjectEntry> bySubject = List.of();

        if (thresholdMet) {
            List<Object[]> mvRows = ratingStatsMap.get(q.getId());
            if (mvRows != null) {
                // Global view: subject rows already loaded from MV batch read.
                // avg_stars in index 1; derive overall avg as mean of subject averages weighted
                // by subject_response_count (index 0 provides per-subject count for reference).
                // For the common case where all subjects share the same response pool, the
                // simple average of avg_stars per subject is equivalent to the global avg.
                if (!mvRows.isEmpty()) {
                    avg = mvRows.stream()
                            .mapToDouble(r -> ((Number) r[1]).doubleValue())
                            .average()
                            .orElse(0.0);
                }
                bySubject = mvRows.stream()
                        .map(row -> new QuestionReportDto.RatingSubjectEntry(
                                (String) row[0],
                                ((Number) row[1]).doubleValue()))
                        .toList();
            } else {
                // Scoped view: live JPQL queries.
                avg = pathPrefix != null
                        ? ratingRepo.findAverageByQuestionIdAndPath(q.getId(), pathPrefix).orElse(null)
                        : ratingRepo.findAverageByQuestionId(q.getId()).orElse(null);
                List<Object[]> rows = pathPrefix != null
                        ? ratingRepo.findAverageBySubjectForQuestionAndPath(q.getId(), pathPrefix)
                        : ratingRepo.findAverageBySubjectForQuestion(q.getId());
                bySubject = rows.stream()
                        .map(row -> new QuestionReportDto.RatingSubjectEntry(
                                (String) row[0],
                                ((Number) row[1]).doubleValue()))
                        .toList();
            }
        }

        return new QuestionReportDto(
                q.getId(), q.getBody(), q.getQuestionType(), q.getDisplayOrder(),
                responseCount, thresholdMet,
                null, List.of(),
                avg, bySubject,
                List.of(),
                null, null
        );
    }

    private QuestionReportDto buildTextReport(Question q, long responseCount, boolean thresholdMet,
                                               boolean canViewText, String pathPrefix) {
        List<String> textResponses;
        if (!canViewText) {
            textResponses = null;           // no permission — omit entirely
        } else if (!thresholdMet) {
            textResponses = List.of();      // permission granted, but below threshold
        } else {
            textResponses = pathPrefix != null
                    ? textRepo.findTextValuesByQuestionIdAndPath(q.getId(), pathPrefix)
                    : textRepo.findTextValuesByQuestionId(q.getId());
        }

        return new QuestionReportDto(
                q.getId(), q.getBody(), q.getQuestionType(), q.getDisplayOrder(),
                responseCount, thresholdMet,
                null, List.of(),
                null, List.of(),
                List.of(),
                thresholdMet ? responseCount : null,
                textResponses
        );
    }

    private Collection<String> extractAuthoritiesFromContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return java.util.Collections.emptySet();
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(java.util.stream.Collectors.toSet());
    }
}
