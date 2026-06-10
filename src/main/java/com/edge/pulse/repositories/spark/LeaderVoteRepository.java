package com.edge.pulse.repositories.spark;

import com.edge.pulse.data.models.spark.LeaderVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaderVoteRepository extends JpaRepository<LeaderVote, UUID> {

    List<LeaderVote> findByLeaderIdAndAwardPeriodId(UUID leaderId, UUID awardPeriodId);

    List<LeaderVote> findByAwardPeriodId(UUID awardPeriodId);

    List<LeaderVote> findByAwardPeriodIdAndCategoryId(UUID awardPeriodId, String categoryId);

    Optional<LeaderVote> findByAwardPeriodIdAndCategoryIdAndLeaderId(
            UUID awardPeriodId, String categoryId, UUID leaderId);

    boolean existsByAwardPeriodIdAndCategoryIdAndLeaderId(
            UUID awardPeriodId, String categoryId, UUID leaderId);

    /**
     * Count votes for a specific user (via nomination) in a given period and category.
     * LeaderVote.nominee is a Nomination FK, so we join through nominee.nominee.id to reach the User.
     */
    @Query("SELECT COUNT(v) FROM LeaderVote v WHERE v.awardPeriod.id = :periodId " +
           "AND v.category.id = :categoryId AND v.nominee.nominee.id = :userId")
    long countVotesForUserInCategory(
            @Param("periodId") UUID periodId,
            @Param("categoryId") String categoryId,
            @Param("userId") UUID userId);
}
