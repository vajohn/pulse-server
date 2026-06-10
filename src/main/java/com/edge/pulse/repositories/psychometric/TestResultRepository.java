package com.edge.pulse.repositories.psychometric;

import com.edge.pulse.data.enums.TestResultStatus;
import com.edge.pulse.data.models.psychometric.TestResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface TestResultRepository extends JpaRepository<TestResult, UUID> {

    Optional<TestResult> findBySessionId(UUID sessionId);

    @Query("""
        SELECT tr FROM TestResult tr
        JOIN FETCH tr.test
        JOIN FETCH tr.session rs
        JOIN FETCH rs.user
        WHERE tr.id = :id
    """)
    Optional<TestResult> findByIdWithSessionAndTest(@Param("id") UUID id);

    @Query("""
        SELECT tr FROM TestResult tr
        JOIN FETCH tr.test
        JOIN FETCH tr.session rs
        WHERE rs.user.id = :userId
        ORDER BY tr.scoredAt DESC NULLS LAST
    """)
    List<TestResult> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query(value = """
            SELECT tr FROM TestResult tr
            JOIN FETCH tr.test
            JOIN FETCH tr.session rs
            JOIN FETCH rs.user u
            WHERE tr.test.id = :testId
            ORDER BY tr.scoredAt DESC NULLS LAST
            """,
            countQuery = "SELECT COUNT(tr) FROM TestResult tr WHERE tr.test.id = :testId")
    Page<TestResult> findByTestId(@Param("testId") UUID testId, Pageable pageable);

    @Query(value = """
            SELECT tr FROM TestResult tr
            JOIN FETCH tr.test
            JOIN FETCH tr.session rs
            JOIN FETCH rs.user u
            WHERE tr.test.id = :testId AND tr.status = :status
            ORDER BY tr.scoredAt DESC NULLS LAST
            """,
            countQuery = "SELECT COUNT(tr) FROM TestResult tr WHERE tr.test.id = :testId AND tr.status = :status")
    Page<TestResult> findByTestIdAndStatus(@Param("testId") UUID testId,
                                           @Param("status") TestResultStatus status,
                                           Pageable pageable);

    long countByTestIdAndStatus(UUID testId, TestResultStatus status);

    @Query(value = """
            SELECT tr FROM TestResult tr
            JOIN FETCH tr.test
            JOIN FETCH tr.session rs
            JOIN FETCH rs.user u
            LEFT JOIN u.orgUnit ou
            WHERE (ou.path = :pathPrefix OR ou.path LIKE CONCAT(:pathPrefix, '/%'))
            ORDER BY tr.scoredAt DESC NULLS LAST
            """,
            countQuery = """
            SELECT COUNT(tr) FROM TestResult tr
            JOIN tr.session rs JOIN rs.user u
            LEFT JOIN u.orgUnit ou
            WHERE (ou.path = :pathPrefix OR ou.path LIKE CONCAT(:pathPrefix, '/%'))
            """)
    List<TestResult> findByOrgPathPrefix(@Param("pathPrefix") String pathPrefix, Pageable pageable);
}
