package com.edge.pulse.repositories.psychometric;

import com.edge.pulse.data.enums.ResultAudience;
import com.edge.pulse.data.models.psychometric.ResultVisibilityPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResultVisibilityPolicyRepository extends JpaRepository<ResultVisibilityPolicy, UUID> {

    List<ResultVisibilityPolicy> findByTestId(UUID testId);

    Optional<ResultVisibilityPolicy> findByTestIdAndAudience(UUID testId, ResultAudience audience);
}
