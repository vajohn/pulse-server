package com.edge.pulse.services;

import com.edge.pulse.repositories.UserOrgUnitRepository;
import com.edge.pulse.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SfRoleDerivationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserOrgUnitRepository userOrgUnitRepository;

    private SfRoleDerivationService service;

    @BeforeEach
    void setUp() {
        service = new SfRoleDerivationService(userRepository, userOrgUnitRepository);
    }

    private static final String HC_DEPT  = "Human Capital";
    private static final String ENG_DEPT = "Engineering";

    private Set<String> derive(String dept, String title) {
        // null azureAdId skips the DB leader check — all derivation logic is exercised
        return service.deriveRolesFromSfProfile(null, dept, title);
    }

    @Test
    void derive_plainEmployee_onlyBaseline() {
        Set<String> roles = derive(ENG_DEPT, "Software Engineer");
        assertThat(roles).containsExactlyInAnyOrder(
                "SURVEY_RESPONDENT", "ASSESSMENT_CANDIDATE",
                "PEER_NOMINATOR", "BROADCAST_VIEWER", "SPARK_VOTER");
    }

    @Test
    void derive_nonHrManager_baselinePlusScopeTeamLead() {
        Set<String> roles = derive("Operations", "Operations Manager");
        assertThat(roles).contains("SCOPE_TEAM_LEAD");
        assertThat(roles).doesNotContain("FORM_AUTHOR", "SURVEY_ANALYST", "DIRECTORY_ADMIN");
    }

    @Test
    void derive_hcbp_entityScopeAndAnalytics() {
        Set<String> roles = derive(HC_DEPT, "HR Business Partner");
        assertThat(roles).contains("SCOPE_ENTITY_LEAD", "FORM_ASSIGNER", "SURVEY_ANALYST");
        assertThat(roles).doesNotContain("FORM_AUTHOR", "DIRECTORY_ADMIN", "ASSESSMENT_ADMIN");
    }

    @Test
    void derive_ld_formAuthorAndAnalytics() {
        Set<String> roles = derive(HC_DEPT, "L&D Specialist");
        assertThat(roles).contains("FORM_AUTHOR", "FORM_ASSIGNER", "SURVEY_ANALYST");
        assertThat(roles).doesNotContain("DIRECTORY_ADMIN", "ASSESSMENT_ADMIN");
    }

    @Test
    void derive_er_analystOnly() {
        Set<String> roles = derive(HC_DEPT, "Employee Relations Specialist");
        assertThat(roles).contains("SURVEY_ANALYST");
        assertThat(roles).doesNotContain("FORM_AUTHOR", "DIRECTORY_ADMIN", "ASSESSMENT_ADMIN");
    }

    @Test
    void derive_hrOps_directoryAdmin() {
        Set<String> roles = derive(HC_DEPT, "HR Operations Coordinator");
        assertThat(roles).contains("DIRECTORY_ADMIN");
        assertThat(roles).doesNotContain("FORM_AUTHOR", "ASSESSMENT_ADMIN", "SPARK_ADMIN");
    }

    @Test
    void derive_psychologist_assessmentAdmin() {
        Set<String> roles = derive(HC_DEPT, "Organisational Psychologist");
        assertThat(roles).contains("ASSESSMENT_ADMIN");
        assertThat(roles).doesNotContain("DIRECTORY_ADMIN", "FORM_AUTHOR");
    }

    @Test
    void derive_rewards_sparkAdmin() {
        Set<String> roles = derive(HC_DEPT, "Rewards & Recognition Manager");
        assertThat(roles).contains("SPARK_ADMIN", "SCOPE_TEAM_LEAD");
        assertThat(roles).doesNotContain("BROADCAST_AUTHOR", "FORM_AUTHOR");
    }

    @Test
    void derive_comms_broadcastAuthor() {
        Set<String> roles = derive(HC_DEPT, "Internal Communications Specialist");
        assertThat(roles).contains("BROADCAST_AUTHOR");
        assertThat(roles).doesNotContain("SPARK_ADMIN", "FORM_AUTHOR");
    }

    @Test
    void derive_hrDirector_fullCapabilityStack() {
        Set<String> roles = derive(HC_DEPT, "HR Director");
        assertThat(roles).contains(
                "SCOPE_ENTITY_LEAD", "FORM_AUTHOR", "FORM_ASSIGNER",
                "SURVEY_ANALYST", "ASSESSMENT_ADMIN", "DIRECTORY_ADMIN", "SPARK_ADMIN",
                "SCOPE_TEAM_LEAD");
    }

    @Test
    void derive_driverInHcDivision_onlyBaseline() {
        // CRITICAL: dept matches HC but title has no HR signal — must NOT get HR roles
        Set<String> roles = derive(HC_DEPT, "Driver");
        assertThat(roles).containsExactlyInAnyOrder(
                "SURVEY_RESPONDENT", "ASSESSMENT_CANDIDATE",
                "PEER_NOMINATOR", "BROADCAST_VIEWER", "SPARK_VOTER");
    }

    @Test
    void derive_hrDirectorWithComms_getsAllDirectorRolesPlusBroadcastAuthor() {
        Set<String> roles = derive(HC_DEPT, "HR Director, Communications");
        assertThat(roles).contains("BROADCAST_AUTHOR");
        assertThat(roles).contains("FORM_AUTHOR", "SURVEY_ANALYST", "ASSESSMENT_ADMIN");
    }

    @Test
    void derive_neverAutoAssignsPrivilegedRoles() {
        // These roles must NEVER be auto-assigned under any SF profile
        Set<String> allPossible = derive(HC_DEPT, "HR Director, Communications");
        assertThat(allPossible).doesNotContain(
                "SURVEY_RESULT_VIEWER", "ASSESSMENT_RESULT_VIEWER",
                "SURVEY_TEXT_ANALYST", "ROLE_ADMINISTRATOR");
    }

    @Test
    void derive_baselineAlwaysPresent_evenForHrDirector() {
        Set<String> roles = derive(HC_DEPT, "HR Director");
        assertThat(roles).contains(
                "SURVEY_RESPONDENT", "ASSESSMENT_CANDIDATE",
                "PEER_NOMINATOR", "BROADCAST_VIEWER", "SPARK_VOTER");
    }

    @Test
    void derive_itOperationsInHcDivision_noDirectoryAdmin() {
        // Guard: IT Operations title should not get DIRECTORY_ADMIN even if dept is HC
        Set<String> roles = derive(HC_DEPT, "IT Operations Engineer");
        assertThat(roles).doesNotContain("DIRECTORY_ADMIN");
    }
}
