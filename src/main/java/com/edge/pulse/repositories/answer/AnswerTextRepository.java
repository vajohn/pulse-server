package com.edge.pulse.repositories.answer;

import com.edge.pulse.data.models.answer.AnswerText;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnswerTextRepository extends JpaRepository<AnswerText, UUID> {

    Optional<AnswerText> findBySubmissionId(UUID submissionId);

    List<AnswerText> findBySubmissionIdIn(Collection<UUID> submissionIds);

    @Query("SELECT COUNT(a) FROM AnswerText a " +
           "WHERE a.submission.question.id = :qid " +
           "AND a.submission.isCurrent = true " +
           "AND a.submission.session.completedAt IS NOT NULL")
    long countByQuestionId(@Param("qid") UUID questionId);

    @Query("SELECT COUNT(a) FROM AnswerText a " +
           "WHERE a.submission.question.id = :qid " +
           "AND a.submission.isCurrent = true " +
           "AND a.submission.session.completedAt IS NOT NULL " +
           "AND (a.submission.session.user.orgUnit.path = :pathPrefix " +
           "     OR a.submission.session.user.orgUnit.path LIKE CONCAT(:pathPrefix, '/%'))")
    long countByQuestionIdAndPath(@Param("qid") UUID questionId, @Param("pathPrefix") String pathPrefix);

    @Query("SELECT a.value FROM AnswerText a " +
           "WHERE a.submission.question.id = :qid " +
           "AND a.submission.isCurrent = true " +
           "AND a.submission.session.completedAt IS NOT NULL")
    List<String> findTextValuesByQuestionId(@Param("qid") UUID questionId);

    @Query("SELECT a.value FROM AnswerText a " +
           "WHERE a.submission.question.id = :qid " +
           "AND a.submission.isCurrent = true " +
           "AND a.submission.session.completedAt IS NOT NULL " +
           "AND (a.submission.session.user.orgUnit.path = :pathPrefix " +
           "     OR a.submission.session.user.orgUnit.path LIKE CONCAT(:pathPrefix, '/%'))")
    List<String> findTextValuesByQuestionIdAndPath(@Param("qid") UUID questionId,
                                                   @Param("pathPrefix") String pathPrefix);
}
