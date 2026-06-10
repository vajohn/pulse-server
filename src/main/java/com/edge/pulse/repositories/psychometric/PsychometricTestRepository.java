package com.edge.pulse.repositories.psychometric;

import com.edge.pulse.data.enums.TestStatus;
import com.edge.pulse.data.models.psychometric.PsychometricTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PsychometricTestRepository extends JpaRepository<PsychometricTest, UUID> {

    Optional<PsychometricTest> findByFormId(UUID formId);

    List<PsychometricTest> findByStatus(TestStatus status);

    Page<PsychometricTest> findByStatus(TestStatus status, Pageable pageable);

    @Query("SELECT pt FROM PsychometricTest pt JOIN FETCH pt.form WHERE pt.id = :id")
    Optional<PsychometricTest> findByIdWithForm(@Param("id") UUID id);

    boolean existsByFormId(UUID formId);

    @Query("SELECT t FROM PsychometricTest t WHERE t.form.id IN :formIds")
    List<PsychometricTest> findByFormIdIn(@Param("formIds") Collection<UUID> formIds);

    /** List all tests with their forms eagerly — eliminates N+1 on form access during listTests(). */
    @Query(value = "SELECT pt FROM PsychometricTest pt JOIN FETCH pt.form",
           countQuery = "SELECT COUNT(pt) FROM PsychometricTest pt")
    Page<PsychometricTest> findAllWithForm(Pageable pageable);

    /** List tests filtered by status with their forms eagerly. */
    @Query(value = "SELECT pt FROM PsychometricTest pt JOIN FETCH pt.form WHERE pt.status = :status",
           countQuery = "SELECT COUNT(pt) FROM PsychometricTest pt WHERE pt.status = :status")
    Page<PsychometricTest> findByStatusWithForm(@Param("status") TestStatus status, Pageable pageable);

    /**
     * Counts the number of scales defined for the test that owns the given form.
     * Used by the assignment guard for PSYCHOMETRIC forms — scales are the meaningful
     * unit of content for psychometric tests (not survey questions).
     */
    @Query("SELECT COUNT(ps) FROM PsychometricScale ps WHERE ps.test.form.id = :formId")
    long countScalesByFormId(@Param("formId") UUID formId);
}
