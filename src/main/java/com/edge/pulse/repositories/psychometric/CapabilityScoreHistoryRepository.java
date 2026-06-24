package com.edge.pulse.repositories.psychometric;

import com.edge.pulse.data.models.psychometric.CapabilityScoreHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CapabilityScoreHistoryRepository
        extends JpaRepository<CapabilityScoreHistory, UUID> {

    /** Full series for one user+test, oldest→newest, for the trend endpoint. */
    List<CapabilityScoreHistory> findByUserIdAndTestIdOrderByScoredAtAsc(UUID userId, UUID testId);

    boolean existsByScaleIdAndResultId(UUID scaleId, UUID resultId);
}
