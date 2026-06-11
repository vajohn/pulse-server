package com.edge.pulse.data.dto.safrecon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.UUID;

/** Mirrors the fields Pulse needs from saf-recon EmployeeDto (incl. the job-field enrichment). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SafReconEmployee(
        UUID id, String sfUserId, String firstName, String lastName, String fullName,
        String workEmail, String status, String employeeType, LocalDate hireDate,
        UUID managerId, UUID orgUnitId,
        String department, String companyCode, String jobTitle, String jobFunction) {}
