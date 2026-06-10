package com.edge.pulse.repositories;

import com.edge.pulse.data.enums.RoleChangeStatus;
import com.edge.pulse.data.models.RoleChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RoleChangeRequestRepository extends JpaRepository<RoleChangeRequest, UUID> {
    List<RoleChangeRequest> findByStatus(RoleChangeStatus status);

    long countByStatus(RoleChangeStatus status);
}
