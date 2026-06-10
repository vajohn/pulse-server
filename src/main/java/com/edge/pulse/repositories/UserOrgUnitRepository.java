package com.edge.pulse.repositories;

import com.edge.pulse.data.models.UserOrgUnit;
import com.edge.pulse.data.models.UserOrgUnitId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserOrgUnitRepository extends JpaRepository<UserOrgUnit, UserOrgUnitId> {

    @Query("SELECT u FROM UserOrgUnit u WHERE u.user.id = :userId")
    List<UserOrgUnit> findByUserId(@Param("userId") UUID userId);

    @Query("SELECT u FROM UserOrgUnit u WHERE u.orgUnit.id = :orgUnitId")
    List<UserOrgUnit> findByOrgUnitId(@Param("orgUnitId") UUID orgUnitId);

    boolean existsByUserIdAndIsLeaderTrue(UUID userId);
}
