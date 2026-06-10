package com.edge.pulse.repositories.spark;

import com.edge.pulse.data.models.spark.SparkWinner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SparkWinnerRepository extends JpaRepository<SparkWinner, UUID> {

    List<SparkWinner> findByAwardPeriodId(UUID awardPeriodId);

    List<SparkWinner> findByAwardPeriodIdAndAnnouncedAtIsNotNull(UUID awardPeriodId);

    Optional<SparkWinner> findByAwardPeriodIdAndCategoryId(UUID awardPeriodId, String categoryId);

    boolean existsByAwardPeriodIdAndCategoryId(UUID awardPeriodId, String categoryId);

    long countByAwardPeriodIdAndFinalizedAtIsNotNull(UUID awardPeriodId);
}
