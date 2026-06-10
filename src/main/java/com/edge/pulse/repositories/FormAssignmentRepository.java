package com.edge.pulse.repositories;

import com.edge.pulse.data.enums.FormType;
import com.edge.pulse.data.models.FormAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FormAssignmentRepository extends JpaRepository<FormAssignment, UUID> {

    List<FormAssignment> findByFormIdAndActiveTrue(UUID formId);

    List<FormAssignment> findByActiveFalseOrderByAssignedAtDesc();

    /**
     * Find all visible assignments for a user, respecting:
     * - active flag
     * - visibility window (startsAt / expiresAt)
     * - org unit path matching with includeChildren
     */
    @Query("""
        SELECT DISTINCT fa FROM FormAssignment fa
        LEFT JOIN fa.orgUnit ou
        WHERE fa.active = true
          AND (fa.startsAt IS NULL OR fa.startsAt <= CURRENT_TIMESTAMP)
          AND (fa.allowResubmission = true OR fa.expiresAt IS NULL OR fa.expiresAt > CURRENT_TIMESTAMP)
          AND (
            fa.user.id = :userId
            OR (fa.user IS NULL AND :orgPath LIKE CONCAT(ou.path, '%')
                AND (fa.includeChildren = true OR ou.path = :orgPath))
          )
    """)
    List<FormAssignment> findVisibleAssignmentsForUser(@Param("userId") UUID userId, @Param("orgPath") String orgPath);

    /**
     * Same as {@link #findVisibleAssignmentsForUser} but filtered by form type.
     * Used to split Surveys tab (SURVEY) from Tests tab (PSYCHOMETRIC).
     * Bypasses the user-assignments cache — caller must not cache filtered results separately.
     */
    @Query("""
        SELECT DISTINCT fa FROM FormAssignment fa
        LEFT JOIN fa.orgUnit ou
        WHERE fa.active = true
          AND (fa.startsAt IS NULL OR fa.startsAt <= CURRENT_TIMESTAMP)
          AND (fa.allowResubmission = true OR fa.expiresAt IS NULL OR fa.expiresAt > CURRENT_TIMESTAMP)
          AND (
            fa.user.id = :userId
            OR (fa.user IS NULL AND :orgPath LIKE CONCAT(ou.path, '%')
                AND (fa.includeChildren = true OR ou.path = :orgPath))
          )
          AND fa.form.formType = :formType
    """)
    List<FormAssignment> findVisibleAssignmentsForUserByType(
            @Param("userId") UUID userId,
            @Param("orgPath") String orgPath,
            @Param("formType") FormType formType);

    boolean existsByFormIdAndUserIdAndActiveTrue(UUID formId, UUID userId);

    boolean existsByFormIdAndOrgUnitIdAndActiveTrue(UUID formId, UUID orgUnitId);

    long countByFormIdAndActiveTrue(UUID formId);

    List<FormAssignment> findByActiveTrueOrderByAssignedAtDesc();

    /**
     * Returns true if the user has at least one active, in-window assignment for the form,
     * either as a direct user assignment or via their org unit (with optional child inclusion).
     */
    @Query("""
        SELECT COUNT(fa) > 0 FROM FormAssignment fa
        LEFT JOIN fa.orgUnit ou
        WHERE fa.form.id = :formId
          AND fa.active = true
          AND (fa.startsAt IS NULL OR fa.startsAt <= CURRENT_TIMESTAMP)
          AND (fa.allowResubmission = true OR fa.expiresAt IS NULL OR fa.expiresAt > CURRENT_TIMESTAMP)
          AND (
            fa.user.id = :userId
            OR (fa.user IS NULL AND :orgPath LIKE CONCAT(ou.path, '%')
                AND (fa.includeChildren = true OR ou.path = :orgPath))
          )
    """)
    boolean hasVisibleAssignment(@Param("formId") UUID formId,
                                 @Param("userId") UUID userId,
                                 @Param("orgPath") String orgPath);
}
