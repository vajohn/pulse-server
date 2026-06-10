package com.edge.pulse.repositories.spark;

import com.edge.pulse.data.models.spark.SparkCongratulation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SparkCongratulationRepository extends JpaRepository<SparkCongratulation, UUID> {

    List<SparkCongratulation> findBySparkWinnerId(UUID winnerId);

    long countBySparkWinnerId(UUID winnerId);

    Optional<SparkCongratulation> findBySparkWinnerIdAndUserId(UUID winnerId, UUID userId);

    boolean existsBySparkWinnerIdAndUserId(UUID winnerId, UUID userId);

    @Query("SELECT sc.sparkWinner.id, COUNT(sc) FROM SparkCongratulation sc " +
           "WHERE sc.sparkWinner.id IN :winnerIds GROUP BY sc.sparkWinner.id")
    List<Object[]> countGroupedBySparkWinnerIds(@Param("winnerIds") Collection<UUID> winnerIds);
}
