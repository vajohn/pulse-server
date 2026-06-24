package com.edge.pulse.repositories.psychometric;

import com.edge.pulse.data.models.psychometric.AssessmentCadence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AssessmentCadenceRepository extends JpaRepository<AssessmentCadence, UUID> {
    List<AssessmentCadence> findByTestIdAndActiveTrue(UUID testId);
    List<AssessmentCadence> findByActiveTrue();
}
