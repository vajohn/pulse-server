package com.edge.pulse.services.psychometric;

import com.edge.pulse.data.dto.psychometric.CadenceConfigDto;
import com.edge.pulse.data.dto.psychometric.CadenceConfigRequest;
import com.edge.pulse.data.models.OrganizationalUnit;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.psychometric.AssessmentCadence;
import com.edge.pulse.data.models.psychometric.PsychometricTest;
import com.edge.pulse.repositories.OrganizationalUnitRepository;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.repositories.psychometric.AssessmentCadenceRepository;
import com.edge.pulse.repositories.psychometric.PsychometricTestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Admin CRUD for {@link AssessmentCadence} — micro-engagement delivery rhythm per test+population
 * (Phase 3, D2). Gated by the {@code ASSESS_*} authorities on {@code AdminPsychometricController}.
 */
@Service
@RequiredArgsConstructor
public class CadenceAdminService {

    private final AssessmentCadenceRepository cadenceRepository;
    private final PsychometricTestRepository testRepository;
    private final OrganizationalUnitRepository orgUnitRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<CadenceConfigDto> list(UUID testId) {
        return cadenceRepository.findByTestIdAndActiveTrue(testId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public CadenceConfigDto create(UUID testId, CadenceConfigRequest req, UUID createdById) {
        PsychometricTest test = testRepository.findById(testId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Test not found: " + testId));

        OrganizationalUnit orgUnit = null;
        if (req.orgUnitId() != null) {
            orgUnit = orgUnitRepository.findById(req.orgUnitId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Org unit not found: " + req.orgUnitId()));
        }
        User createdBy = createdById != null
                ? userRepository.findById(createdById).orElse(null)
                : null;

        AssessmentCadence cadence = AssessmentCadence.builder()
                .test(test)
                .cadence(req.cadence())
                .maxItemsPerAdmin(req.maxItemsPerAdmin())
                .orgUnit(orgUnit)
                .includeChildren(req.includeChildren())
                .startsAt(req.startsAt())
                .endsAt(req.endsAt())
                .active(true)
                .createdBy(createdBy)
                .build();
        return toDto(cadenceRepository.save(cadence));
    }

    @Transactional
    public void deactivate(UUID testId, UUID cadenceId) {
        AssessmentCadence cadence = cadenceRepository.findById(cadenceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Cadence not found: " + cadenceId));
        if (!cadence.getTest().getId().equals(testId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Cadence " + cadenceId + " does not belong to test " + testId);
        }
        cadence.setActive(false);
        cadenceRepository.save(cadence);
    }

    private CadenceConfigDto toDto(AssessmentCadence c) {
        return new CadenceConfigDto(
                c.getId(), c.getTest().getId(), c.getCadence(), c.getMaxItemsPerAdmin(),
                c.getOrgUnit() != null ? c.getOrgUnit().getId() : null,
                c.isIncludeChildren(), c.getStartsAt(), c.getEndsAt(), c.isActive());
    }
}
