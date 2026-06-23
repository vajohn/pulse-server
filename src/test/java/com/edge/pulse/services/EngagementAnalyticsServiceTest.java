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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
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
 * <p>Covers the COMPOSITE engagement score (C-1): SCALE answers normalized in SQL
 * (returned as sum/count pairs and per-form/distribution rows) PLUS per-submission
 * RATING rows normalized in-service to 1..5, combined into one composite. Asserts:
 * the normalization math (a known RATING + known SCALE → expected composite),
 * both-source respondents and distribution, per-form & global suppression
 * (&lt;5 masking still holds over the combined data), trend over both sources, the
 * I-3 SCOPE_ENTITY cross-entity denial, and the eligible-user denominator (C-2).
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

    /** N distinct fresh session ids (for the respondent-set union). */
    private List<UUID> sessionIds(int n) {
        return java.util.stream.Stream.generate(UUID::randomUUID).limit(n).toList();
    }

    /**
     * Sets the SecurityContext authorities that {@code resolveScopedNode} /
     * {@code resolvePathPrefix} read directly (they query the live context, not the
     * {@code hasBroadScope} mock). SCOPE_ORG_WIDE = unrestricted node resolution.
     */
    private void authContext(UUID userId, String... authorities) {
        var auths = List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, auths));
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Default-stubs the SCALE-source window queries to "empty" so each test only has to
     * stub what it cares about. Marked lenient — individual tests override the relevant
     * scope/window matchers. The current window uses exactOnly per the test; the prior
     * window is empty unless a test stubs it.
     */
    @BeforeEach
    void emptyScaleWindowsByDefault() {
        lenient().when(scaleRepo.sumNormalizedInWindow(any(), anyBoolean(), any(), any()))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{0.0, 0L}));
        lenient().when(scaleRepo.findNormalizedFormSumsInWindow(any(), anyBoolean(), any(), any()))
                .thenReturn(List.of());
        lenient().when(scaleRepo.findRespondentFormSessionsInWindow(any(), anyBoolean(), any(), any()))
                .thenReturn(List.of());
        lenient().when(scaleRepo.findNormalizedDistributionInWindow(any(), anyBoolean(), any(), any()))
                .thenReturn(List.of());
        lenient().when(scaleRepo.findRespondentSessionIdsInWindow(any(), anyBoolean(), any(), any()))
                .thenReturn(List.of());
        lenient().when(ratingRepo.findSubmissionRatingsInWindow(any(), anyBoolean(), any(), any()))
                .thenReturn(List.of());
    }

    // -----------------------------------------------------------------------
    // Min-team-size suppression (MANDATORY) — over the COMBINED data
    // -----------------------------------------------------------------------

    @Test
    void engagement_belowThreshold_returnsMaskedWithNoAggregates() {
        OrganizationalUnit ou = orgUnit("/EDGE/OPS");
        UUID userId = UUID.randomUUID();
        authContext(userId, "SCOPE_ORG_WIDE");
        when(orgUnitRepository.findById(ou.getId())).thenReturn(java.util.Optional.of(ou));

        // 2 SCALE respondents + 2 RATING respondents (distinct) = 4 < MIN_RESPONDENTS(5)
        List<UUID> scaleSessions = sessionIds(2);
        when(scaleRepo.findRespondentSessionIdsInWindow(eq("/EDGE/OPS"), eq(false), any(), any()))
                .thenReturn(scaleSessions);
        when(scaleRepo.sumNormalizedInWindow(eq("/EDGE/OPS"), eq(false), any(), any()))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{8.0, 2L}));
        when(ratingRepo.findSubmissionRatingsInWindow(eq("/EDGE/OPS"), eq(false), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"Survey A", UUID.randomUUID(), 4.0, 5},
                        new Object[]{"Survey A", UUID.randomUUID(), 4.0, 5}));

        EngagementSummaryDto dto = service.getEngagementSummary("TEAM", ou.getId(), true, 30, userId);

        assertThat(dto.masked()).isTrue();
        assertThat(dto.respondents()).isNull();
        assertThat(dto.overallScore()).isNull();
        assertThat(dto.categoryScores()).isNull();
        assertThat(dto.scoreDistribution()).isNull();
        assertThat(dto.trend()).isNull();
        assertThat(dto.nodeId()).isEqualTo(ou.getId());
        assertThat(dto.scopeLevel()).isEqualTo("TEAM");
    }

    // -----------------------------------------------------------------------
    // COMPOSITE normalization math: known RATING + known SCALE → expected score
    // -----------------------------------------------------------------------

    @Test
    void engagement_combinesNormalizedScaleAndRating_withExpectedComposite() {
        OrganizationalUnit ou = orgUnit("/EDGE/OPS");
        UUID userId = UUID.randomUUID();
        authContext(userId, "SCOPE_ORG_WIDE");
        when(orgUnitRepository.findById(ou.getId())).thenReturn(java.util.Optional.of(ou));

        // SCALE source: SQL already normalized → sum=12.0 over 3 answers (mean 4.0).
        when(scaleRepo.sumNormalizedInWindow(eq("/EDGE/OPS"), eq(false), any(), any()))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{12.0, 3L}));
        when(scaleRepo.findNormalizedFormSumsInWindow(eq("/EDGE/OPS"), eq(false), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"Pulse Q1", 12.0, 3L}));
        // SCALE distribution: three normalized-4 answers
        when(scaleRepo.findNormalizedDistributionInWindow(eq("/EDGE/OPS"), eq(false), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{4, 3L}));
        List<UUID> scaleSessions = sessionIds(3);
        when(scaleRepo.findRespondentSessionIdsInWindow(eq("/EDGE/OPS"), eq(false), any(), any()))
                .thenReturn(scaleSessions);
        // Per-form SCALE [title, sessionId] rows — same 3 sessions, so the per-form set unions
        // with the 2 RATING sessions to 5 distinct respondents for "Pulse Q1".
        when(scaleRepo.findRespondentFormSessionsInWindow(eq("/EDGE/OPS"), eq(false), any(), any()))
                .thenReturn(scaleSessions.stream()
                        .map(sid -> new Object[]{"Pulse Q1", sid}).toList());

        // RATING source: 2 submissions (distinct sessions) on a 10-star scale.
        //   stars 10 / max 10 → 1 + 4*(10-1)/(10-1) = 5.0
        //   stars 5.5 / max 10 → 1 + 4*(5.5-1)/9 = 1 + 4*4.5/9 = 1 + 2.0 = 3.0
        when(ratingRepo.findSubmissionRatingsInWindow(eq("/EDGE/OPS"), eq(false), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"Pulse Q1", UUID.randomUUID(), 10.0, 10},
                        new Object[]{"Pulse Q1", UUID.randomUUID(), 5.5, 10}));

        when(userRepository.countActiveInSubtree("/EDGE/OPS")).thenReturn(10L);

        EngagementSummaryDto dto = service.getEngagementSummary("TEAM", ou.getId(), true, 30, userId);

        assertThat(dto.masked()).isFalse();
        // 5 distinct respondents = 3 scale + 2 rating
        assertThat(dto.respondents()).isEqualTo(5);
        // overall = (scaleSum 12.0 + ratingNorm 5.0 + 3.0) / (3 + 2) = 20.0 / 5 = 4.0
        assertThat(dto.overallScore()).isCloseTo(4.0, within(1e-9));
        // participation = 5 / 10 * 100
        assertThat(dto.participationRate()).isEqualTo(50.0);

        // distribution: SCALE bucket 4 (×3) + RATING buckets 5 (×1) and 3 (×1)
        assertThat(dto.scoreDistribution()).extracting(EngagementSummaryDto.ScoreBucket::score)
                .containsExactly(3, 4, 5);
        assertThat(dto.scoreDistribution()).extracting(EngagementSummaryDto.ScoreBucket::count)
                .containsExactly(1L, 3L, 1L);

        // single combined category "Pulse Q1": sum (12.0 + 5.0 + 3.0)/(3+2)=4.0; 5 respondents
        assertThat(dto.categoryScores()).hasSize(1);
        assertThat(dto.categoryScores().get(0).category()).isEqualTo("Pulse Q1");
        assertThat(dto.categoryScores().get(0).meanScore()).isCloseTo(4.0, within(1e-9));
        assertThat(dto.categoryScores().get(0).respondents()).isEqualTo(5);
        assertThat(dto.enps()).isNull();
    }

    // -----------------------------------------------------------------------
    // Per-form (category) suppression over combined data
    // -----------------------------------------------------------------------

    @Test
    void engagement_perFormBelowThreshold_isSuppressedFromCategoryScores() {
        OrganizationalUnit ou = orgUnit("/EDGE/OPS");
        UUID userId = UUID.randomUUID();
        authContext(userId, "SCOPE_ORG_WIDE");
        when(orgUnitRepository.findById(ou.getId())).thenReturn(java.util.Optional.of(ou));

        when(scaleRepo.sumNormalizedInWindow(eq("/EDGE/OPS"), eq(false), any(), any()))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{44.0, 11L}));
        when(scaleRepo.findNormalizedFormSumsInWindow(eq("/EDGE/OPS"), eq(false), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"Big Form", 32.0, 8L},    // kept (8 respondents below)
                        new Object[]{"Tiny Form", 12.0, 3L})); // dropped (3 respondents below)
        // Per-form distinct respondents: Big Form = 8 (kept), Tiny Form = 3 (<5, dropped).
        when(scaleRepo.findRespondentFormSessionsInWindow(eq("/EDGE/OPS"), eq(false), any(), any()))
                .thenAnswer(inv -> {
                    LocalDateTime until = inv.getArgument(3);
                    if (!until.isAfter(LocalDateTime.now().minusDays(1))) return List.of();
                    List<Object[]> rows = new java.util.ArrayList<>();
                    sessionIds(8).forEach(s -> rows.add(new Object[]{"Big Form", s}));
                    sessionIds(3).forEach(s -> rows.add(new Object[]{"Tiny Form", s}));
                    return rows;
                });
        // Current window (until ≈ now) has respondents; prior window (until ≈ 30 days ago)
        // is empty → NO_PRIOR_DATA. Branch on the `until` arg to distinguish the two calls.
        when(scaleRepo.findRespondentSessionIdsInWindow(eq("/EDGE/OPS"), eq(false), any(), any()))
                .thenAnswer(inv -> {
                    LocalDateTime until = inv.getArgument(3);
                    return until.isAfter(LocalDateTime.now().minusDays(1)) ? sessionIds(11) : List.of();
                });
        when(userRepository.countActiveInSubtree("/EDGE/OPS")).thenReturn(20L);

        EngagementSummaryDto dto = service.getEngagementSummary("TEAM", ou.getId(), true, 30, userId);

        assertThat(dto.categoryScores()).hasSize(1);
        assertThat(dto.categoryScores().get(0).category()).isEqualTo("Big Form");
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
        authContext(userId, "SCOPE_ORG_WIDE");
        when(orgUnitRepository.findById(ou.getId())).thenReturn(java.util.Optional.of(ou));

        // exactOnly = true expected when includeChildren = false
        when(scaleRepo.sumNormalizedInWindow(eq("/EDGE/OPS"), eq(true), any(), any()))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{18.0, 6L}));
        when(scaleRepo.findRespondentSessionIdsInWindow(eq("/EDGE/OPS"), eq(true), any(), any()))
                .thenReturn(sessionIds(6));
        when(userRepository.countByOrgUnitIdAndActiveTrue(ou.getId())).thenReturn(6L);

        EngagementSummaryDto dto = service.getEngagementSummary("TEAM", ou.getId(), false, 30, userId);

        assertThat(dto.masked()).isFalse();
        assertThat(dto.includeChildren()).isFalse();
        assertThat(dto.eligibleUsers()).isEqualTo(6);
        assertThat(dto.participationRate()).isEqualTo(100.0);
        assertThat(dto.overallScore()).isCloseTo(3.0, within(1e-9));
    }

    @Test
    void engagement_global_broadScopeNoNode_usesNullPathAndActiveUserCount() {
        UUID userId = UUID.randomUUID();
        authContext(userId, "SCOPE_ORG_WIDE");
        // No-node fallback now gates on SCOPE_ORG_WIDE directly (not hasBroadScope).

        // current window
        when(scaleRepo.sumNormalizedInWindow(isNull(), eq(false), any(), any()))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{195.0, 50L}));  // mean 3.9
        when(scaleRepo.findRespondentSessionIdsInWindow(isNull(), eq(false), any(), any()))
                .thenReturn(sessionIds(50));
        when(userRepository.countByActiveTrue()).thenReturn(100L);
        // prior window: same mean → FLAT. We must stub BOTH sum and respondent ids for prev.
        // Distinguish windows is awkward with any(); supply prior respondents via a count >=5
        // by stubbing the same method to also return a set on the prior call is not possible
        // with a single matcher, so we assert FLAT by making prior overall equal.
        // Use a separate path-agnostic stub for the prior respondent-id set:
        // (the @BeforeEach default returns empty → NO_PRIOR_DATA). Override here:
        // Both windows share the isNull/false matcher, so the stub returns the same value for
        // both calls (current and prior) → prior overall == current → FLAT, prior respondents=50.

        EngagementSummaryDto dto = service.getEngagementSummary(null, null, true, 30, userId);

        assertThat(dto.masked()).isFalse();
        assertThat(dto.scopeLevel()).isEqualTo("GLOBAL");
        assertThat(dto.nodeId()).isNull();
        assertThat(dto.eligibleUsers()).isEqualTo(100);
        assertThat(dto.participationRate()).isEqualTo(50.0);
        assertThat(dto.overallScore()).isCloseTo(3.9, within(1e-9));
        // current 3.9 vs prior 3.9 (same stub for both windows) → FLAT
        assertThat(dto.trend().direction()).isEqualTo("FLAT");
    }

    // -----------------------------------------------------------------------
    // Trend over combined data
    // -----------------------------------------------------------------------

    @Test
    void engagement_trendUp_whenCurrentExceedsPrior() {
        OrganizationalUnit ou = orgUnit("/EDGE/OPS");
        UUID userId = UUID.randomUUID();
        authContext(userId, "SCOPE_ORG_WIDE");
        when(orgUnitRepository.findById(ou.getId())).thenReturn(java.util.Optional.of(ou));

        // Current window has the later `since`. We separate windows by the `until` argument:
        // current window until = now (~today), prior window until = curStart (~30 days ago).
        // Use Mockito Answer to branch on the `until` argument.
        when(scaleRepo.sumNormalizedInWindow(eq("/EDGE/OPS"), eq(false), any(), any()))
                .thenAnswer(inv -> {
                    LocalDateTime until = inv.getArgument(3);
                    // current window's `until` is the latest; prior window's `until` is earlier.
                    boolean current = until.isAfter(LocalDateTime.now().minusDays(1));
                    return current
                            ? java.util.List.<Object[]>of(new Object[]{30.0, 6L}) /*5.0*/
                            : java.util.List.<Object[]>of(new Object[]{18.0, 6L}) /*3.0*/;
                });
        when(scaleRepo.findRespondentSessionIdsInWindow(eq("/EDGE/OPS"), eq(false), any(), any()))
                .thenReturn(sessionIds(6));
        when(userRepository.countActiveInSubtree("/EDGE/OPS")).thenReturn(6L);

        EngagementSummaryDto dto = service.getEngagementSummary("TEAM", ou.getId(), true, 30, userId);

        assertThat(dto.overallScore()).isCloseTo(5.0, within(1e-9));
        assertThat(dto.trend().direction()).isEqualTo("UP");
        assertThat(dto.trend().previousScore()).isCloseTo(3.0, within(1e-9));
        assertThat(dto.trend().delta()).isCloseTo(2.0, within(1e-9));
    }

    // -----------------------------------------------------------------------
    // I-3 SECURITY: SCOPE_ENTITY caller CANNOT read another entity's node
    // -----------------------------------------------------------------------

    @Test
    void engagement_scopeEntityCaller_cannotReadForeignEntityNode() {
        // Caller is SCOPE_ENTITY (NOT org-wide). They request a node in another entity.
        OrganizationalUnit foreign = orgUnit("/EDGE/OTHER_ENTITY/HR");
        OrganizationalUnit ownUnit = orgUnit("/EDGE/MY_ENTITY/OPS");
        User caller = userIn(ownUnit);
        authContext(caller.getId(), "SCOPE_ENTITY");  // entity-scoped, NOT org-wide

        when(orgUnitRepository.findById(foreign.getId())).thenReturn(java.util.Optional.of(foreign));
        // hasBroadScope is true for SCOPE_ENTITY, but the no-node fallback now gates on
        // SCOPE_ORG_WIDE only — SCOPE_ENTITY must NOT get the global aggregate.
        lenient().when(orgUnitScopeService.hasBroadScope(anyCollection())).thenReturn(true);
        // The caller's accessible org units (company_code set) do NOT include the foreign node,
        // so resolveScopedNode rejects it → node==null → no-node fallback.
        when(orgUnitScopeService.resolveAccessibleOrgUnitIds(eq(caller.getId()), anyCollection()))
                .thenReturn(List.of(ownUnit.getId()));
        // The fallback resolves the caller's OWN subtree path (bypassing resolvePathPrefix's
        // broad-scope shortcut), so the user must be loadable.
        when(userRepository.findById(caller.getId())).thenReturn(java.util.Optional.of(caller));

        // Foreign node has plenty of (would-be-leaked) data — must NOT be surfaced.
        lenient().when(scaleRepo.sumNormalizedInWindow(eq(foreign.getPath()), anyBoolean(), any(), any()))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{500.0, 100L}));
        lenient().when(scaleRepo.findRespondentSessionIdsInWindow(eq(foreign.getPath()), anyBoolean(), any(), any()))
                .thenReturn(sessionIds(100));

        // CRITICAL: the GLOBAL (null-path) data path returns REAL, well-above-threshold data.
        // BEFORE the fix, SCOPE_ENTITY fell through to this global aggregate (hasBroadScope==true)
        // and would have leaked the entire org's 60-respondent dataset. The test now PROVES the
        // fallback does NOT consume this global path.
        lenient().when(scaleRepo.sumNormalizedInWindow(isNull(), anyBoolean(), any(), any()))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{240.0, 60L}));  // global mean 4.0
        lenient().when(scaleRepo.findRespondentSessionIdsInWindow(isNull(), anyBoolean(), any(), any()))
                .thenReturn(sessionIds(60));
        lenient().when(userRepository.countByActiveTrue()).thenReturn(500L);

        // The caller's OWN subtree (/EDGE/MY_ENTITY/OPS) is what the fallback MUST bind to.
        // Give it its own distinct, above-threshold data so we can positively assert the
        // result reflects ONLY this scope — never the global/foreign aggregate.
        when(scaleRepo.sumNormalizedInWindow(eq(ownUnit.getPath()), eq(false), any(), any()))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{17.5, 5L}));  // own mean 3.5
        when(scaleRepo.findRespondentSessionIdsInWindow(eq(ownUnit.getPath()), eq(false), any(), any()))
                .thenReturn(sessionIds(5));
        when(userRepository.countActiveInSubtree(ownUnit.getPath())).thenReturn(20L);

        EngagementSummaryDto dto = service.getEngagementSummary("ENTITY", foreign.getId(), true, 30, caller.getId());

        // The foreign node is NOT resolved/echoed; no foreign data leaks.
        assertThat(dto.nodeId()).isNull();
        assertThat(dto.nodeName()).isNull();

        // PROOF OF NO LEAK: the scope used is the caller's OWN subtree, NOT the global aggregate.
        // - respondents = 5 (own subtree), NOT 60 (global) and NOT 100 (foreign).
        // - overall = 3.5 (own mean), NOT 4.0 (global/foreign mean).
        // - eligibleUsers = 20 (own countActiveInSubtree), NOT 500 (global countByActiveTrue).
        assertThat(dto.masked()).isFalse();
        assertThat(dto.respondents()).isEqualTo(5);
        assertThat(dto.overallScore()).isCloseTo(3.5, within(1e-9));
        assertThat(dto.eligibleUsers()).isEqualTo(20);

        // Assert the actual scope passed to the repository: the global (null) path was NEVER
        // queried for the respondent set, and the foreign path was never queried either.
        org.mockito.Mockito.verify(scaleRepo, org.mockito.Mockito.never())
                .findRespondentSessionIdsInWindow(isNull(), anyBoolean(), any(), any());
        org.mockito.Mockito.verify(scaleRepo, org.mockito.Mockito.never())
                .findRespondentSessionIdsInWindow(eq(foreign.getPath()), anyBoolean(), any(), any());
        // The own-subtree path WAS the scope used (called for current + prior windows).
        org.mockito.Mockito.verify(scaleRepo, org.mockito.Mockito.atLeastOnce())
                .findRespondentSessionIdsInWindow(eq(ownUnit.getPath()), eq(false),
                        any(), any());
    }

    @Test
    void engagement_scopeEntityCaller_canReadOwnAccessibleNode() {
        // Same SCOPE_ENTITY caller, but requesting a node that IS in their accessible set.
        OrganizationalUnit ownNode = orgUnit("/EDGE/MY_ENTITY/OPS");
        User caller = userIn(ownNode);
        authContext(caller.getId(), "SCOPE_ENTITY");

        when(orgUnitRepository.findById(ownNode.getId())).thenReturn(java.util.Optional.of(ownNode));
        when(orgUnitScopeService.resolveAccessibleOrgUnitIds(eq(caller.getId()), anyCollection()))
                .thenReturn(List.of(ownNode.getId()));

        when(scaleRepo.sumNormalizedInWindow(eq(ownNode.getPath()), eq(false), any(), any()))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{20.0, 5L}));
        when(scaleRepo.findRespondentSessionIdsInWindow(eq(ownNode.getPath()), eq(false), any(), any()))
                .thenReturn(sessionIds(5));
        when(userRepository.countActiveInSubtree(ownNode.getPath())).thenReturn(10L);

        EngagementSummaryDto dto = service.getEngagementSummary("ENTITY", ownNode.getId(), true, 30, caller.getId());

        assertThat(dto.masked()).isFalse();
        assertThat(dto.nodeId()).isEqualTo(ownNode.getId());
        assertThat(dto.respondents()).isEqualTo(5);
        assertThat(dto.overallScore()).isCloseTo(4.0, within(1e-9));
    }

    // -----------------------------------------------------------------------
    // Caller scope-bounding: narrow-scope caller requesting an out-of-scope node
    // -----------------------------------------------------------------------

    @Test
    void engagement_narrowScopeCaller_outOfScopeNode_fallsBackToOwnSubtree() {
        OrganizationalUnit requested = orgUnit("/EDGE/HR");
        OrganizationalUnit ownUnit = orgUnit("/EDGE/OPS");
        User caller = userIn(ownUnit);
        authContext(caller.getId(), "SCOPE_TEAM");

        when(orgUnitRepository.findById(requested.getId())).thenReturn(java.util.Optional.of(requested));
        // hasBroadScope no longer consulted in the no-node fallback (gates on SCOPE_ORG_WIDE).
        lenient().when(orgUnitScopeService.hasBroadScope(anyCollection())).thenReturn(false);
        // Narrow caller: requested node not in accessible set → rejected.
        when(orgUnitScopeService.resolveAccessibleOrgUnitIds(eq(caller.getId()), anyCollection()))
                .thenReturn(List.of(ownUnit.getId()));
        when(userRepository.findById(caller.getId())).thenReturn(java.util.Optional.of(caller));

        // After falling back, the scope is the caller's own subtree path /EDGE/OPS.
        when(scaleRepo.sumNormalizedInWindow(eq("/EDGE/OPS"), eq(false), any(), any()))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{24.5, 7L}));
        when(scaleRepo.findRespondentSessionIdsInWindow(eq("/EDGE/OPS"), eq(false), any(), any()))
                .thenReturn(sessionIds(7));
        when(userRepository.countActiveInSubtree("/EDGE/OPS")).thenReturn(7L);

        EngagementSummaryDto dto = service.getEngagementSummary("ENTITY", requested.getId(), true, 30, caller.getId());

        // The requested out-of-scope node is NOT echoed; resolved scope is the caller's own.
        assertThat(dto.masked()).isFalse();
        assertThat(dto.nodeId()).isNull();
        assertThat(dto.eligibleUsers()).isEqualTo(7);
        // M-4: the score is computed from the caller's OWN subtree (24.5/7 = 3.5),
        // never from the foreign node — a positive assertion that no foreign data leaked.
        assertThat(dto.overallScore()).isCloseTo(3.5, within(1e-9));
    }

    // -----------------------------------------------------------------------
    // M-3: scopeLevel validation against the OrgLevel enum
    // -----------------------------------------------------------------------

    @Test
    void engagement_nonsenseScopeLevel_normalizedToGlobal() {
        UUID userId = UUID.randomUUID();
        authContext(userId, "SCOPE_ORG_WIDE");
        // No-node fallback now gates on SCOPE_ORG_WIDE directly (not hasBroadScope).
        when(scaleRepo.sumNormalizedInWindow(isNull(), eq(false), any(), any()))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{0.0, 0L}));
        when(scaleRepo.findRespondentSessionIdsInWindow(isNull(), eq(false), any(), any()))
                .thenReturn(List.of());

        EngagementSummaryDto dto = service.getEngagementSummary("'; DROP TABLE--", null, true, 30, userId);

        // Nonsense (and unsafe) scope label is NOT echoed back; normalized to GLOBAL.
        assertThat(dto.scopeLevel()).isEqualTo("GLOBAL");
    }
}
