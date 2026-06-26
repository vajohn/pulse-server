package com.edge.pulse.services.psychometric;

import com.edge.pulse.data.dto.psychometric.CadenceConfigRequest;
import com.edge.pulse.data.enums.Cadence;
import com.edge.pulse.data.enums.TestStatus;
import com.edge.pulse.data.models.psychometric.PsychometricTest;
import com.edge.pulse.repositories.OrganizationalUnitRepository;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.repositories.psychometric.AssessmentCadenceRepository;
import com.edge.pulse.repositories.psychometric.PsychometricTestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * TDD gate tests for Task 12: CadenceAdminService.create() must reject
 * any non-ACTIVE psychometric test with 409 CONFLICT.
 */
@ExtendWith(MockitoExtension.class)
class CadenceAdminServiceGateTest {

    @Mock private AssessmentCadenceRepository cadenceRepository;
    @Mock private PsychometricTestRepository testRepository;
    @Mock private OrganizationalUnitRepository orgUnitRepository;
    @Mock private UserRepository userRepository;

    private CadenceAdminService service;

    private static final UUID TEST_ID  = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();

    private CadenceConfigRequest validReq() {
        return new CadenceConfigRequest(Cadence.WEEKLY, 10, null, true, null, null);
    }

    @BeforeEach
    void setUp() {
        service = new CadenceAdminService(
                cadenceRepository, testRepository, orgUnitRepository, userRepository);
    }

    // ------------------------------------------------------------------
    // Rejection cases
    // ------------------------------------------------------------------

    @Test
    void cannotAssignDraftTest_409() {
        PsychometricTest draft = PsychometricTest.builder()
                .id(TEST_ID).status(TestStatus.DRAFT).build();
        when(testRepository.findById(TEST_ID)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.create(TEST_ID, validReq(), ADMIN_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    org.junit.jupiter.api.Assertions.assertEquals(
                            HttpStatus.CONFLICT, rse.getStatusCode());
                    org.junit.jupiter.api.Assertions.assertTrue(
                            rse.getReason() != null &&
                            rse.getReason().toLowerCase().contains("active"),
                            "Error message must mention ACTIVE");
                });
    }

    @Test
    void cannotAssignPendingApprovalTest_409() {
        PsychometricTest pending = PsychometricTest.builder()
                .id(TEST_ID).status(TestStatus.PENDING_APPROVAL).build();
        when(testRepository.findById(TEST_ID)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.create(TEST_ID, validReq(), ADMIN_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    org.junit.jupiter.api.Assertions.assertEquals(
                            HttpStatus.CONFLICT, rse.getStatusCode());
                });
    }

    @Test
    void cannotAssignRetiredTest_409() {
        PsychometricTest retired = PsychometricTest.builder()
                .id(TEST_ID).status(TestStatus.RETIRED).build();
        when(testRepository.findById(TEST_ID)).thenReturn(Optional.of(retired));

        assertThatThrownBy(() -> service.create(TEST_ID, validReq(), ADMIN_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    org.junit.jupiter.api.Assertions.assertEquals(
                            HttpStatus.CONFLICT, rse.getStatusCode());
                });
    }

    // ------------------------------------------------------------------
    // Happy path — ACTIVE test proceeds past the gate
    // ------------------------------------------------------------------

    @Test
    void canAssignActiveTest_proceedsPastGate() {
        PsychometricTest active = PsychometricTest.builder()
                .id(TEST_ID).status(TestStatus.ACTIVE).build();
        when(testRepository.findById(TEST_ID)).thenReturn(Optional.of(active));
        // userRepository returns empty → service falls through (no NPE on null User)
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.empty());
        when(cadenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Should NOT throw a 409; any subsequent error (e.g. missing org unit) is fine
        // because the status gate passed.
        assertThatCode(() -> service.create(TEST_ID, validReq(), ADMIN_ID))
                .doesNotThrowAnyException();
    }
}
