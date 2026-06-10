package com.edge.pulse.services;

import com.edge.pulse.data.models.User;
import com.edge.pulse.repositories.UserOrgUnitRepository;
import com.edge.pulse.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Derives thin RBAC roles from a user's SF SuccessFactors profile (department + job title).
 *
 * <p>IMPORTANT: Uses title-based signals IN ADDITION to department check.
 * Department alone is insufficient — 362 non-HR employees exist in HC divisions
 * (drivers, cleaners, guards). Title check is mandatory before any HR capability grant.
 *
 * <p>NEVER auto-assigns: SURVEY_RESULT_VIEWER, ASSESSMENT_RESULT_VIEWER,
 * SURVEY_TEXT_ANALYST, ROLE_ADMINISTRATOR.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SfRoleDerivationService {

    private final UserRepository userRepository;
    private final UserOrgUnitRepository userOrgUnitRepository;

    public Set<String> deriveRolesFromSfProfile(String azureAdId, String department, String jobTitle) {
        Set<String> derived = new HashSet<>();
        String dept  = department != null ? department.toLowerCase() : "";
        String title = jobTitle   != null ? jobTitle.toLowerCase()   : "";

        // ── Tier 1: default baseline — all users unconditionally ──────────────
        derived.addAll(List.of(
                "SURVEY_RESPONDENT", "ASSESSMENT_CANDIDATE",
                "PEER_NOMINATOR", "BROADCAST_VIEWER", "SPARK_VOTER"
        ));

        // ── People manager — any department ───────────────────────────────────
        // SF isLeader flag (from user_org_unit) OR managerial title. SCOPE only.
        boolean isLeader = false;
        if (azureAdId != null) {
            Optional<User> existingUser = userRepository.findByAzureAdId(azureAdId);
            if (existingUser.isPresent() && existingUser.get().getId() != null) {
                isLeader = userOrgUnitRepository.existsByUserIdAndIsLeaderTrue(existingUser.get().getId());
            }
        }
        if (isLeader || isManagerialTitle(title)) {
            derived.add("SCOPE_TEAM_LEAD");
        }

        // ── HR capability roles — dept check + title signal (BOTH required) ───
        // WARNING: Do not grant HR capability roles on dept check alone.
        // 362 non-HR employees (drivers, cleaners, guards) are in HC divisions.
        boolean inHrDept = dept.contains("human capital") || dept.contains("human resources")
                || dept.contains("hc ") || dept.startsWith("hc");

        if (inHrDept) {
            // HR Senior Leadership — broadest HR capability stack
            if (isSeniorLeadershipTitle(title)) {
                derived.addAll(List.of(
                        "SCOPE_ENTITY_LEAD", "FORM_AUTHOR", "FORM_ASSIGNER",
                        "SURVEY_ANALYST", "ASSESSMENT_ADMIN", "DIRECTORY_ADMIN", "SPARK_ADMIN"
                ));
            }
            // HR Business Partner — entity-scoped, form assignment, analytics
            if (isHcbpTitle(title)) {
                derived.addAll(List.of("SCOPE_ENTITY_LEAD", "FORM_ASSIGNER", "SURVEY_ANALYST"));
            }
            // HR L&D / Talent Development / OD — form authoring and analytics
            if (isLdTitle(title)) {
                derived.addAll(List.of("FORM_AUTHOR", "FORM_ASSIGNER", "SURVEY_ANALYST"));
            }
            // HR Employee Relations — survey analytics within scope
            if (isErTitle(title)) {
                derived.add("SURVEY_ANALYST");
            }
            // HR Operations / People Ops — user and org directory management
            if (isHrOpsTitle(title)) {
                derived.add("DIRECTORY_ADMIN");
            }
            // HR Psychometric / Assessment team
            if (isPsychometricTitle(title)) {
                derived.add("ASSESSMENT_ADMIN");
            }
            // HR Rewards / Recognition — Spark programme management
            if (isRewardsTitle(title)) {
                derived.add("SPARK_ADMIN");
            }
            // HR Communications / Comms / PR — broadcast authoring
            if (isCommsTitle(title)) {
                derived.add("BROADCAST_AUTHOR");
            }
        }

        log.info("SF role derivation: azureAdId={} dept='{}' title='{}' → roles={}",
                azureAdId, dept, title, derived);
        return derived;
    }

    private boolean isManagerialTitle(String t) {
        return t.contains("manager") || t.contains("head of")
                || t.contains("svp") || t.contains("vp ")
                || t.contains("vice president") || t.contains("executive")
                || t.contains("director") || t.contains("chief");
    }

    private boolean isSeniorLeadershipTitle(String t) {
        return t.contains("director") || t.contains("svp")
                || t.contains("vp ") || t.contains("head of")
                || t.contains("chief") || t.contains("executive");
    }

    private boolean isHcbpTitle(String t) {
        return t.contains("business partner") || t.contains("hcbp");
    }

    private boolean isLdTitle(String t) {
        return t.contains("l&d") || t.contains("learning")
                || t.contains("talent development") || t.contains("organisational development")
                || t.contains("organizational development") || t.contains("talent dev");
    }

    private boolean isErTitle(String t) {
        // " er " with spaces prevents matching "performance" or "career"
        return t.contains("employee relations") || t.contains(" er ");
    }

    private boolean isHrOpsTitle(String t) {
        // Guard against non-HR operations titles that may appear in HC divisions
        // for seconded technical staff (e.g. "field operations", "it operations").
        return (t.contains("operations") || t.contains("onboarding") || t.contains("data quality"))
                && !t.contains("field operations") && !t.contains("it operations");
    }

    private boolean isPsychometricTitle(String t) {
        return t.contains("psychologist") || t.contains("psychometric")
                || t.contains("assessment specialist");
    }

    private boolean isRewardsTitle(String t) {
        return t.contains("rewards") || t.contains("recognition") || t.contains("awards");
    }

    private boolean isCommsTitle(String t) {
        return t.contains("communications") || t.contains("comms")
                || t.matches(".*\\bpr\\b.*");
    }
}
