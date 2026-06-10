package com.edge.pulse.repositories.spark;

import com.edge.pulse.data.models.spark.Nomination;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NominationRepository extends JpaRepository<Nomination, UUID> {

    List<Nomination> findByNominatorIdAndAwardPeriodId(UUID nominatorId, UUID awardPeriodId);

    List<Nomination> findByNominatorId(UUID nominatorId);

    List<Nomination> findByAwardPeriodId(UUID awardPeriodId);

    List<Nomination> findByAwardPeriodIdAndCategoryId(UUID awardPeriodId, String categoryId);

    Optional<Nomination> findByAwardPeriodIdAndCategoryIdAndNominatorId(
            UUID awardPeriodId, String categoryId, UUID nominatorId);

    boolean existsByAwardPeriodIdAndCategoryIdAndNominatorId(
            UUID awardPeriodId, String categoryId, UUID nominatorId);

    @Query("SELECT n FROM Nomination n WHERE n.awardPeriod.id = :periodId " +
           "AND n.nominee.orgUnit.id = :orgUnitId")
    List<Nomination> findByPeriodAndOrgUnit(
            @Param("periodId") UUID periodId,
            @Param("orgUnitId") UUID orgUnitId);

    @Query("SELECT COUNT(n) FROM Nomination n WHERE n.awardPeriod.id = :periodId " +
           "AND n.nominee.id = :nomineeId AND n.category.id = :categoryId")
    long countByPeriodAndNomineeAndCategory(
            @Param("periodId") UUID periodId,
            @Param("nomineeId") UUID nomineeId,
            @Param("categoryId") String categoryId);

    @Query("SELECT COUNT(DISTINCT n.category.id) FROM Nomination n WHERE n.awardPeriod.id = :periodId")
    long countDistinctCategoriesWithNominations(@Param("periodId") UUID periodId);
}
