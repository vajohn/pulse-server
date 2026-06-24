package com.edge.pulse.repositories.psychometric;

import com.edge.pulse.data.models.psychometric.ScaleProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScaleProgressRepository extends JpaRepository<ScaleProgress, UUID> {
    List<ScaleProgress> findByUserIdAndTestId(UUID userId, UUID testId);
    Optional<ScaleProgress> findByUserIdAndScaleIdAndWindowId(UUID userId, UUID scaleId, UUID windowId);
}
