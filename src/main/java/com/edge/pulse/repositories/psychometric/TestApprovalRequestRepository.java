package com.edge.pulse.repositories.psychometric;

import com.edge.pulse.data.enums.TestApprovalStatus;
import com.edge.pulse.data.models.psychometric.TestApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestApprovalRequestRepository extends JpaRepository<TestApprovalRequest, UUID> {

    @Query("SELECT r FROM TestApprovalRequest r " +
           "JOIN FETCH r.test JOIN FETCH r.submittedBy " +
           "WHERE r.status = :status ORDER BY r.submittedAt ASC")
    List<TestApprovalRequest> findByStatusWithDetails(TestApprovalStatus status);

    Optional<TestApprovalRequest> findFirstByTestIdAndStatus(UUID testId, TestApprovalStatus status);

    boolean existsByTestIdAndStatus(UUID testId, TestApprovalStatus status);
}
