package com.edge.pulse.repositories;

import com.edge.pulse.data.enums.OrgLevel;
import com.edge.pulse.data.models.OrganizationalUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationalUnitRepository extends JpaRepository<OrganizationalUnit, UUID> {
    List<OrganizationalUnit> findByParentId(UUID parentId);
    List<OrganizationalUnit> findByOrgLevel(OrgLevel orgLevel);
    List<OrganizationalUnit> findByParentIsNullAndActiveTrue();
    Optional<OrganizationalUnit> findByOrgUnitCode(String orgUnitCode);

    @Query("SELECT o FROM OrganizationalUnit o WHERE o.path LIKE :pathPrefix% AND o.active = true")
    List<OrganizationalUnit> findByPathPrefix(String pathPrefix);

    /**
     * Loads all active org units in a single flat query.
     * Used by AdminService.getOrgTree() to build the tree in-memory
     * instead of triggering N+1 queries via recursive lazy loading.
     */
    List<OrganizationalUnit> findAllByActiveTrue();

    Optional<OrganizationalUnit> findByEntraGroupId(String entraGroupId);

    List<OrganizationalUnit> findBySfExternalCode(String sfExternalCode);

    List<OrganizationalUnit> findAllBySyncSource(String syncSource);

    // ── Scope resolution (SCOPE_ORG_WIDE / SCOPE_ENTITY / SCOPE_TEAM) ───────

    /** Returns all active org unit IDs — used by SCOPE_ORG_WIDE. */
    @Query("SELECT o.id FROM OrganizationalUnit o WHERE o.active = true")
    List<UUID> findAllActiveIds();

    /**
     * Returns all active org unit IDs matching the given company code.
     * Used by SCOPE_ENTITY when the user's company_code is known.
     * Returns an empty list when companyCode is null (no SF sync yet).
     */
    @Query("SELECT o.id FROM OrganizationalUnit o WHERE o.active = true AND o.companyCode = :companyCode")
    List<UUID> findAllActiveIdsByCompanyCode(@Param("companyCode") String companyCode);

    /**
     * Returns the path prefix of the org unit containing the given user.
     * Used by SCOPE_TEAM to compute the subtree for the caller.
     */
    @Query("SELECT CASE WHEN ou.path = '' THEN CAST(ou.id AS string) ELSE CONCAT(ou.path, '/', CAST(ou.id AS string)) END " +
           "FROM OrganizationalUnit ou WHERE ou.id = " +
           "(SELECT u.orgUnit.id FROM User u WHERE u.id = :userId)")
    Optional<String> findPathPrefixByUserId(@Param("userId") UUID userId);

    /**
     * Returns the ID of the org unit containing the given user.
     * Used by the no-scope branch (own data only).
     */
    @Query("SELECT u.orgUnit.id FROM User u WHERE u.id = :userId")
    Optional<UUID> findOrgUnitIdByUserId(@Param("userId") UUID userId);

    /** Returns all active IDs under the given path prefix — used by SCOPE_TEAM. */
    @Query("SELECT o.id FROM OrganizationalUnit o WHERE o.active = true AND (o.path LIKE :pathPrefix% OR o.id = :ownId)")
    List<UUID> findAllActiveIdsByPathPrefix(@Param("pathPrefix") String pathPrefix, @Param("ownId") UUID ownId);
}
