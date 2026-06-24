package com.edge.pulse.repositories.psychometric;

import com.edge.pulse.data.models.psychometric.UserItemExposure;
import com.edge.pulse.data.models.psychometric.UserItemExposureId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface UserItemExposureRepository
        extends JpaRepository<UserItemExposure, UserItemExposureId> {
    List<UserItemExposure> findByUserIdAndTestId(UUID userId, UUID testId);
}
