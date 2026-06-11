package com.edge.pulse.data.dto.safrecon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/** Mirrors saf-recon OrgUnitDto. `level` kept as String — mapped to Pulse OrgLevel during sync. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SafReconOrgUnit(
        UUID id, String sfCode, String name, String level,
        UUID parentId, String path, int depth, String companyCode) {}
