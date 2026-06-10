package com.edge.pulse.repositories;

import com.edge.pulse.data.models.ResponseSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResponseSessionRepository extends JpaRepository<ResponseSession, UUID> {

    /**
     * Returns the most recently started open session for the given user+form pair, or empty.
     * Uses LIMIT 1 internally — safe when called concurrently (unlike the plain findBy...
     * variant which throws IncorrectResultSizeDataAccessException if duplicate open sessions
     * exist during a race). The V2 partial unique index prevents duplicates at DB level;
     * findFirst is the defensive in-code guard.
     *
     * <p>No lock — use this for the initial "does a session exist?" check only.
     * For the race-loser retry path use {@link #findFirstOpenForUpdate}.
     */
    Optional<ResponseSession> findFirstByUserIdAndFormIdAndCompletedAtIsNullOrderByStartedAtDesc(
            UUID userId, UUID formId);

    /**
     * Same as {@link #findFirstByUserIdAndFormIdAndCompletedAtIsNullOrderByStartedAtDesc}
     * but acquires a {@code SELECT ... FOR UPDATE} row lock on the returned row.
     *
     * <p>Use only in the race-loser retry path (after catching
     * {@code DataIntegrityViolationException} from
     * {@link com.edge.pulse.services.SessionCreationHelper#tryCreate}) to prevent
     * a concurrent {@code completeSession()} call from closing the winning session
     * between our find and our return.
     *
     * <p>Callers must pass {@code PageRequest.of(0, 1)} to enforce LIMIT 1 at the SQL
     * level — matching the defensive behaviour of the unlocked derived query above.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rs FROM ResponseSession rs " +
           "WHERE rs.user.id = :userId " +
           "AND rs.form.id = :formId " +
           "AND rs.completedAt IS NULL " +
           "ORDER BY rs.startedAt DESC")
    List<ResponseSession> findFirstOpenForUpdate(
            @Param("userId") UUID userId, @Param("formId") UUID formId, Pageable pageable);

    Optional<ResponseSession> findByAnonIdentityIdAndCompletedAtIsNull(UUID anonIdentityId);

    @Query("SELECT rs FROM ResponseSession rs " +
           "WHERE rs.user.id = :userId " +
           "AND rs.form.id = :formId " +
           "AND rs.completedAt IS NOT NULL " +
           "ORDER BY rs.completedAt DESC")
    List<ResponseSession> findCompletedByUserAndForm(
            @Param("userId") UUID userId,
            @Param("formId") UUID formId);

    /** Find the most recent session (open or completed) for a user + form. */
    @Query("SELECT rs FROM ResponseSession rs " +
           "WHERE rs.user.id = :userId " +
           "AND rs.form.id = :formId " +
           "ORDER BY rs.startedAt DESC")
    List<ResponseSession> findByUserIdAndFormId(
            @Param("userId") UUID userId,
            @Param("formId") UUID formId);

    /** Batch: all sessions for a user across multiple forms (used by AssignmentService). */
    @Query("SELECT rs FROM ResponseSession rs " +
           "WHERE rs.user.id = :userId " +
           "AND rs.form.id IN :formIds " +
           "ORDER BY rs.startedAt DESC")
    List<ResponseSession> findByUserIdAndFormIdIn(
            @Param("userId") UUID userId,
            @Param("formIds") Collection<UUID> formIds);

    long countByCompletedAtIsNotNull();

    @Query("SELECT COUNT(rs) FROM ResponseSession rs WHERE rs.form.id = :formId AND rs.completedAt IS NOT NULL")
    long countCompletedByForm(@Param("formId") UUID formId);

    @Query("SELECT COUNT(rs) FROM ResponseSession rs WHERE rs.form.id = :formId AND rs.completedAt IS NULL")
    long countInProgressByForm(@Param("formId") UUID formId);

    @Query("SELECT COUNT(rs) FROM ResponseSession rs WHERE rs.completedAt IS NOT NULL")
    long countAllCompleted();

    @Query("SELECT COUNT(rs) FROM ResponseSession rs WHERE rs.completedAt IS NOT NULL AND rs.isAnonymous = true")
    long countAllCompletedAnonymous();

    @Query("SELECT COUNT(rs) FROM ResponseSession rs WHERE rs.completedAt IS NOT NULL AND rs.isAnonymous = false")
    long countAllCompletedIdentified();

    @Query("SELECT COUNT(rs) FROM ResponseSession rs WHERE rs.form.id = :fid AND rs.completedAt IS NOT NULL AND rs.isAnonymous = :anon")
    long countCompletedByFormAndAnonymous(@Param("fid") UUID formId, @Param("anon") boolean anonymous);

    @Query("SELECT COUNT(rs) FROM ResponseSession rs WHERE rs.form.id = :fid AND rs.completedAt IS NOT NULL " +
           "AND (rs.user.orgUnit.path = :pathPrefix OR rs.user.orgUnit.path LIKE CONCAT(:pathPrefix, '/%'))")
    long countCompletedByFormAndPath(@Param("fid") UUID formId, @Param("pathPrefix") String pathPrefix);

    @Query("SELECT COUNT(rs) FROM ResponseSession rs WHERE rs.form.id = :fid AND rs.completedAt IS NULL " +
           "AND (rs.user.orgUnit.path = :pathPrefix OR rs.user.orgUnit.path LIKE CONCAT(:pathPrefix, '/%'))")
    long countInProgressByFormAndPath(@Param("fid") UUID formId, @Param("pathPrefix") String pathPrefix);

    @Query("SELECT COUNT(rs) FROM ResponseSession rs WHERE rs.form.id = :fid AND rs.user.id = :uid AND rs.completedAt IS NOT NULL")
    long countCompletedByFormAndUserId(@Param("fid") UUID formId, @Param("uid") UUID userId);

    @Query("SELECT COUNT(rs) FROM ResponseSession rs WHERE rs.form.id = :fid AND rs.user.id = :uid AND rs.completedAt IS NULL")
    long countInProgressByFormAndUserId(@Param("fid") UUID formId, @Param("uid") UUID userId);

    // -----------------------------------------------------------------------
    // Dashboard analytics: optional path-scope and date-range filtering.
    // pathFilter: exact prefix (e.g. "/EDGE/OPS") — NO trailing %; pass null for global.
    // since: lower-bound date; use LocalDateTime.of(2000,1,1,0,0) to include all records.
    // Queries match the org unit itself (exact) OR any descendant (prefix + "/...") to avoid
    // the boundary ambiguity where LIKE '/EDGE/7001%' would also match '/EDGE/70011'.
    // -----------------------------------------------------------------------
    @Query("SELECT COUNT(rs) FROM ResponseSession rs " +
           "WHERE rs.completedAt IS NOT NULL " +
           "AND (:pathFilter IS NULL OR rs.user.orgUnit.path = :pathFilter " +
           "     OR rs.user.orgUnit.path LIKE CONCAT(:pathFilter, '/%')) " +
           "AND rs.completedAt >= :since")
    long countAllCompletedFiltered(@Param("pathFilter") String pathFilter,
                                   @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(rs) FROM ResponseSession rs " +
           "WHERE rs.completedAt IS NOT NULL AND rs.isAnonymous = true " +
           "AND (:pathFilter IS NULL OR rs.user.orgUnit.path = :pathFilter " +
           "     OR rs.user.orgUnit.path LIKE CONCAT(:pathFilter, '/%')) " +
           "AND rs.completedAt >= :since")
    long countAllCompletedAnonymousFiltered(@Param("pathFilter") String pathFilter,
                                            @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(rs) FROM ResponseSession rs " +
           "WHERE rs.completedAt IS NOT NULL AND rs.isAnonymous = false " +
           "AND (:pathFilter IS NULL OR rs.user.orgUnit.path = :pathFilter " +
           "     OR rs.user.orgUnit.path LIKE CONCAT(:pathFilter, '/%')) " +
           "AND rs.completedAt >= :since")
    long countAllCompletedIdentifiedFiltered(@Param("pathFilter") String pathFilter,
                                             @Param("since") LocalDateTime since);
}
