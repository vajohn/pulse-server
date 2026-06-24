package com.edge.pulse.repositories.psychometric;

import com.edge.pulse.data.models.psychometric.CapabilityProfileCurrent;
import com.edge.pulse.data.models.psychometric.CapabilityProfileCurrentId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CapabilityProfileCurrentRepository
        extends JpaRepository<CapabilityProfileCurrent, CapabilityProfileCurrentId> {

    Optional<CapabilityProfileCurrent> findByUserIdAndScaleId(UUID userId, UUID scaleId);

    /**
     * Cohort rows for a test, restricted to the given accessible org-unit IDs and excluding
     * restricted scales (CWB/validity, D3). Native query: the projection stores user_id/scale_id as
     * raw UUID columns (no @ManyToOne associations), so a native JOIN to {@code users} (org-unit
     * filter) and {@code psychometric_scale} (restricted filter) is the clean form — JPQL cannot
     * join on non-association UUID columns. INVALID results never produce a current row (the history
     * hook skips them, D4), so no validity filter is needed here.
     */
    @Query(value = """
            SELECT c.* FROM capability_profile_current c
              JOIN users u ON u.id = c.user_id
              JOIN psychometric_scale s ON s.id = c.scale_id
            WHERE c.test_id = :testId
              AND s.restricted = false
              AND u.org_unit_id IN (:orgUnitIds)
            """, nativeQuery = true)
    List<CapabilityProfileCurrent> findCohort(@Param("testId") UUID testId,
                                              @Param("orgUnitIds") List<UUID> orgUnitIds);
}
