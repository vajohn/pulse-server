package com.edge.pulse.services;

import com.edge.pulse.data.dto.DirectorySyncResultDto;
import com.edge.pulse.data.dto.SfUserRecord;
import com.edge.pulse.data.enums.OrgLevel;
import com.edge.pulse.data.models.OrganizationalUnit;
import com.edge.pulse.data.models.SfSyncState;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.UserSfProfile;
import com.edge.pulse.repositories.OrganizationalUnitRepository;
import com.edge.pulse.repositories.SfSyncStateRepository;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.repositories.UserSfProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static com.edge.pulse.data.dto.SfUserRecord.extractCode;
import static com.edge.pulse.data.dto.SfUserRecord.extractName;

/**
 * Synchronises SAP SuccessFactors users and org units into the Pulse database.
 *
 * <p>Org hierarchy is reconstructed from denormalized SF User fields:
 * <pre>
 *   ORGANIZATION (root — one per tenant)
 *     └─ GROUP      (custom02 code — e.g. "13000")
 *          └─ ENTITY     (custom01 code — e.g. "4600")
 *               └─ ORG_UNIT  (division  code — e.g. "10001368")
 *                    └─ TEAM      (department code — e.g. "10010992")
 * </pre>
 *
 * <p>Users whose SF org fields are missing/unparseable are placed in the
 * "Unassigned" GROUP org unit (direct child of the ORGANIZATION root).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SfDirectorySyncService {

    private static final String SYNC_SOURCE = "SF";
    private static final String ORG_ROOT_CODE = "EDGE_GROUP_ROOT";
    private static final String UNASSIGNED_CODE = "SF_UNASSIGNED";

    private final SfODataClient sfClient;
    private final UserRepository userRepo;
    private final UserSfProfileRepository sfProfileRepo;
    private final OrganizationalUnitRepository orgUnitRepo;
    private final SfSyncStateRepository syncStateRepo;
    private final FormCacheService cacheService;

    @Value("${pulse.sf.dry-run:false}")
    private boolean dryRun;

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Full sync: fetches all SF users, rebuilds the org tree, upserts everything.
     *
     * <p>No outer transaction — the SF network fetch must not hold a DB connection open.
     * Each Spring Data {@code save()} call carries its own auto-transaction.
     * {@code beginSyncState}/{@code completeSyncState} commit immediately via their own saves.
     */
    public DirectorySyncResultDto fullSync() {
        log.info("SfSync [FULL]: starting");
        SfSyncState state = beginSyncState("FULL");

        try {
            List<SfUserRecord> users = sfClient.fetchAllUsers();
            DirectorySyncResultDto result = performSync(users, state);
            completeSyncState(state, result, "SUCCESS", null);
            log.info("SfSync [FULL]: complete — {}", result);
            return result;
        } catch (Exception e) {
            completeSyncState(state, null, "FAILED", null);
            log.error("SfSync [FULL]: failed — {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Delta sync: fetches only users modified since the last successful sync timestamp.
     * Falls back to full sync if no previous sync is recorded.
     *
     * <p>No outer transaction — same rationale as {@link #fullSync()}.
     */
    public DirectorySyncResultDto deltaSync() {
        log.info("SfSync [DELTA]: starting");
        // Use startedAt of last successful sync as the since-timestamp
        String sinceTimestamp = syncStateRepo.findFirstByStatusOrderByStartedAtDesc("SUCCESS")
                .map(s -> s.getStartedAt().toString().replace("T", "T").substring(0, 19))
                .orElse(null);

        if (sinceTimestamp == null) {
            log.warn("SfSync [DELTA]: no previous successful sync — falling back to full sync");
            return fullSync();
        }

        SfSyncState state = beginSyncState("DELTA");
        try {
            List<SfUserRecord> users = sfClient.fetchDeltaUsers(sinceTimestamp);
            DirectorySyncResultDto result = performSync(users, state);
            completeSyncState(state, result, "SUCCESS", null);
            log.info("SfSync [DELTA]: complete — {}", result);
            return result;
        } catch (Exception e) {
            completeSyncState(state, null, "FAILED", null);
            log.error("SfSync [DELTA]: failed — {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Returns status of the most recent sync run.
     */
    @Transactional(readOnly = true)
    public DirectorySyncResultDto getStatus() {
        return syncStateRepo.findFirstByOrderByStartedAtDesc()
                .map(s -> new DirectorySyncResultDto(
                        s.getUsersProcessed(), s.getUsersCreated(), s.getUsersUpdated(),
                        s.getUsersDeactivated(), s.getOrgUnitsProcessed(), s.getOrgUnitsCreated(),
                        s.getOrgUnitsUpdated(), s.getErrorCount(), s.getCompletedAt()))
                .orElse(new DirectorySyncResultDto(0, 0, 0, 0, 0, 0, 0, 0, null));
    }

    // ── Core sync logic ────────────────────────────────────────────────────

    DirectorySyncResultDto performSync(List<SfUserRecord> sfUsers, SfSyncState state) {
        log.info("SfSync: processing {} users from SF", sfUsers.size());

        // Pass 1: build org tree in memory, upsert org units
        OrgTreeResult tree = buildAndUpsertOrgTree(sfUsers);

        // Pass 2: upsert users linked to their org units
        UserSyncResult userResult = upsertUsers(sfUsers, tree);

        // Pass 3: link managers (SF manager.userId → Pulse user.manager)
        linkManagers(sfUsers);

        return new DirectorySyncResultDto(
                sfUsers.size(),
                userResult.created,
                userResult.updated,
                userResult.deactivated,
                tree.processed,
                tree.created,
                tree.updated,
                userResult.errors,
                LocalDateTime.now(ZoneOffset.UTC)
        );
    }

    // ── Org tree ───────────────────────────────────────────────────────────

    OrgTreeResult buildAndUpsertOrgTree(List<SfUserRecord> sfUsers) {
        // Ensure ORGANIZATION root exists
        OrganizationalUnit root = upsertOrgUnit(
                ORG_ROOT_CODE, "EDGE Group", null, OrgLevel.ORGANIZATION, null, 0);

        // Ensure UNASSIGNED GROUP exists directly under root
        OrganizationalUnit unassigned = upsertOrgUnit(
                UNASSIGNED_CODE, "Unassigned", root, OrgLevel.GROUP, null, 1);

        int created = 0, updated = 0;
        // We track new creations via a simple count before/after approach
        long beforeCount = orgUnitRepo.count();

        // Collect distinct org combinations from users
        // Key: group_code|entity_code|orgunit_code|team_code
        Map<String, OrganizationalUnit> cache = new LinkedHashMap<>();

        for (SfUserRecord u : sfUsers) {
            String groupCode    = extractCode(u.custom02());
            String entityCode   = extractCode(u.custom01());
            String orgUnitCode  = extractCode(u.division());
            String teamCode     = extractCode(u.department());

            if (groupCode == null) continue; // can't build tree without group

            // GROUP — sfCode is the extracted numeric code (safe if different from name)
            String groupKey = "G:" + groupCode;
            if (!cache.containsKey(groupKey)) {
                OrganizationalUnit group = upsertOrgUnit(
                        "SF_G_" + groupCode,
                        extractName(u.custom02()),
                        root, OrgLevel.GROUP, u.custom02(), 1);
                group.setCompanyCode(groupCode);
                cache.put(groupKey, group);
            }
            OrganizationalUnit group = cache.get(groupKey);

            if (entityCode == null) continue;

            // ENTITY
            String entityKey = "E:" + entityCode;
            if (!cache.containsKey(entityKey)) {
                OrganizationalUnit entity = upsertOrgUnit(
                        "SF_E_" + entityCode,
                        extractName(u.custom01()),
                        group, OrgLevel.ENTITY, u.custom01(), 2);
                entity.setCompanyCode(entityCode);
                cache.put(entityKey, entity);
            }
            OrganizationalUnit entity = cache.get(entityKey);

            if (orgUnitCode == null) continue;

            // ORG_UNIT
            String ouKey = "OU:" + orgUnitCode;
            if (!cache.containsKey(ouKey)) {
                cache.put(ouKey, upsertOrgUnit(
                        "SF_OU_" + orgUnitCode,
                        extractName(u.division()),
                        entity, OrgLevel.ORG_UNIT, u.division(), 3));
            }
            OrganizationalUnit orgUnit = cache.get(ouKey);

            if (teamCode == null) continue;

            // TEAM
            String teamKey = "T:" + teamCode;
            if (!cache.containsKey(teamKey)) {
                cache.put(teamKey, upsertOrgUnit(
                        "SF_T_" + teamCode,
                        extractName(u.department()),
                        orgUnit, OrgLevel.TEAM, u.department(), 4));
            }
        }

        long afterCount = orgUnitRepo.count();
        int newUnits = (int) (afterCount - beforeCount);
        // Add 2 for root + unassigned if newly created (beforeCount includes them after upsert)
        int processed = cache.size() + 2;

        return new OrgTreeResult(processed, newUnits, processed - newUnits,
                unassigned, cache);
    }

    /**
     * Upserts a single org unit keyed by orgUnitCode (stable across syncs).
     *
     * @param rawSfValue the raw SF field value e.g. "Al Tariq Tech (4600)" — used to derive
     *                   sfExternalCode only when a code is present in parentheses.
     */
    private OrganizationalUnit upsertOrgUnit(String orgUnitCode, String name,
                                              OrganizationalUnit parent,
                                              OrgLevel level, String rawSfValue, int depth) {
        // Only store sfExternalCode when the value actually contained "(code)" — avoids
        // unique constraint collisions when the same plain name appears at multiple levels.
        String extractedCode = extractCode(rawSfValue);
        String safeCode = (extractedCode != null && !extractedCode.equals(name)) ? extractedCode : null;

        Optional<OrganizationalUnit> existing = orgUnitRepo.findByOrgUnitCode(orgUnitCode);
        if (existing.isPresent()) {
            OrganizationalUnit ou = existing.get();
            boolean changed = false;
            if (name != null && !name.equals(ou.getOrgUnitName())) { ou.setOrgUnitName(name); changed = true; }
            if (!level.equals(ou.getOrgLevel())) { ou.setOrgLevel(level); changed = true; }
            if (safeCode != null && !safeCode.equals(ou.getSfExternalCode())) {
                ou.setSfExternalCode(safeCode); changed = true;
            }
            if (!ou.isActive()) { ou.setActive(true); changed = true; }
            if (changed && !dryRun) orgUnitRepo.save(ou);
            return ou;
        }

        String parentPath = (parent == null) ? "" : buildPath(parent);
        OrganizationalUnit ou = OrganizationalUnit.builder()
                .orgUnitCode(orgUnitCode)
                .orgUnitName(name)
                .parent(parent)
                .orgLevel(level)
                .sfExternalCode(safeCode)
                .syncSource(SYNC_SOURCE)
                .path(parentPath)
                .depth(depth)
                .active(true)
                .build();

        if (!dryRun) {
            ou = orgUnitRepo.save(ou);
            log.debug("SfSync: created org unit [{}] {} ({})", level, name, orgUnitCode);
        }
        return ou;
    }

    private String buildPath(OrganizationalUnit ou) {
        if (ou.getPath() == null || ou.getPath().isBlank()) {
            return "/" + ou.getOrgUnitCode();
        }
        return ou.getPath() + "/" + ou.getOrgUnitCode();
    }

    // ── User upsert ────────────────────────────────────────────────────────

    UserSyncResult upsertUsers(List<SfUserRecord> sfUsers, OrgTreeResult tree) {
        int created = 0, updated = 0, deactivated = 0, errors = 0;

        // Batch-load profiles scoped to users in this batch only.
        // For delta syncs (e.g. 10 changed users) this avoids pulling all 10k profiles.
        Set<String> sfIds = sfUsers.stream()
                .map(SfUserRecord::userId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, UserSfProfile> profileCache = new HashMap<>();
        if (!sfIds.isEmpty()) {
            List<User> existingUsers = userRepo.findAllBySfUserIdIn(sfIds);
            Set<UUID> pulseIds = existingUsers.stream()
                    .map(User::getId).collect(Collectors.toSet());
            if (!pulseIds.isEmpty()) {
                sfProfileRepo.findAllById(pulseIds)
                        .forEach(p -> profileCache.put(p.getUserId(), p));
            }
        }

        for (SfUserRecord sfUser : sfUsers) {
            try {
                String email = sfUser.effectiveEmail();
                if (sfUser.userId() == null || sfUser.userId().isBlank()) {
                    log.debug("SfSync: skipping user with no userId");
                    continue;
                }
                if (email == null || email.isBlank()) {
                    log.debug("SfSync: skipping SF user {} — no usable email", sfUser.userId());
                    errors++;
                    continue;
                }

                OrganizationalUnit orgUnit = resolveOrgUnit(sfUser, tree);

                Optional<User> existingBySfId = userRepo.findBySfUserId(sfUser.userId());
                Optional<User> existingByEmail = existingBySfId.isPresent()
                        ? existingBySfId
                        : userRepo.findByEmail(email);

                boolean isNew = existingByEmail.isEmpty();
                User user = existingByEmail.orElseGet(User::new);

                // Core fields (users table — keep lean)
                user.setSfUserId(sfUser.userId());
                if (user.getEmail() == null) user.setEmail(email);
                if (user.getDisplayName() == null || !user.getDisplayName().equals(sfUser.displayName())) {
                    user.setDisplayName(sfUser.displayName());
                }
                user.setCompanyCode(extractCode(sfUser.custom01()));
                user.setDivision(sfUser.division());
                user.setDepartment(sfUser.department());
                user.setOrgUnit(orgUnit);
                user.setActive(sfUser.isActive());

                if (!dryRun) {
                    User saved = userRepo.save(user);

                    // SF metadata → extension table (never touched by auth/session queries)
                    upsertSfProfile(saved, sfUser, profileCache);

                    // Evict assignment cache when org unit changes
                    if (!isNew) {
                        cacheService.evict(FormCacheService.userAssignmentsKey(saved.getId()));
                    }
                }

                if (!sfUser.isActive()) {
                    deactivated++;
                } else if (isNew) {
                    created++;
                } else {
                    updated++;
                }

            } catch (Exception e) {
                log.warn("SfSync: error processing user {} — {}", sfUser.userId(), e.getMessage());
                errors++;
            }
        }

        log.info("SfSync: users — created={}, updated={}, deactivated={}, errors={}",
                created, updated, deactivated, errors);
        return new UserSyncResult(created, updated, deactivated, errors);
    }

    private OrganizationalUnit resolveOrgUnit(SfUserRecord sfUser, OrgTreeResult tree) {
        // Try TEAM first (most specific), then ORG_UNIT, then ENTITY, then GROUP
        String teamCode  = extractCode(sfUser.department());
        String ouCode    = extractCode(sfUser.division());
        String entityCode = extractCode(sfUser.custom01());
        String groupCode = extractCode(sfUser.custom02());

        for (String key : new String[]{
                teamCode   != null ? "T:"  + teamCode    : null,
                ouCode     != null ? "OU:" + ouCode      : null,
                entityCode != null ? "E:"  + entityCode  : null,
                groupCode  != null ? "G:"  + groupCode   : null
        }) {
            if (key != null && tree.cache.containsKey(key)) {
                return tree.cache.get(key);
            }
        }

        log.debug("SfSync: could not resolve org unit for user {} — placing in Unassigned", sfUser.userId());
        return tree.unassigned;
    }

    private void upsertSfProfile(User user, SfUserRecord sfUser, Map<UUID, UserSfProfile> profileCache) {
        UserSfProfile profile = profileCache.computeIfAbsent(
                user.getId(), id -> UserSfProfile.builder().user(user).build());

        profile.setSfSyncedAt(LocalDateTime.now(ZoneOffset.UTC));
        profile.setSfEmployeeType(sfUser.custom05());
        profile.setSfFunction(sfUser.custom08());

        if (sfUser.hireDate() != null && !sfUser.hireDate().isBlank()) {
            try {
                String raw = sfUser.hireDate();
                if (raw.startsWith("/Date(")) {
                    long epoch = Long.parseLong(raw.replaceAll("[^0-9]", ""));
                    profile.setSfHireDate(java.time.Instant.ofEpochMilli(epoch)
                            .atZone(ZoneOffset.UTC).toLocalDate());
                } else {
                    profile.setSfHireDate(java.time.LocalDate.parse(raw.substring(0, 10)));
                }
            } catch (Exception e) {
                log.debug("SfSync: could not parse hireDate '{}' for user {}", sfUser.hireDate(), sfUser.userId());
            }
        }

        // Replace map entry with the flushed instance so userId is populated on subsequent hits
        profileCache.put(user.getId(), sfProfileRepo.save(profile));
    }

    void linkManagers(List<SfUserRecord> sfUsers) {
        int linked = 0, notFound = 0;
        for (SfUserRecord sfUser : sfUsers) {
            if (sfUser.manager() == null || sfUser.manager().userId() == null) continue;
            try {
                Optional<User> userOpt    = userRepo.findBySfUserId(sfUser.userId());
                Optional<User> managerOpt = userRepo.findBySfUserId(sfUser.manager().userId());
                if (userOpt.isPresent() && managerOpt.isPresent()) {
                    User u = userOpt.get();
                    if (!managerOpt.get().equals(u.getManager())) {
                        u.setManager(managerOpt.get());
                        if (!dryRun) userRepo.save(u);
                        linked++;
                    }
                } else {
                    notFound++;
                }
            } catch (Exception e) {
                log.debug("SfSync: manager link error for user {} — {}", sfUser.userId(), e.getMessage());
            }
        }
        log.info("SfSync: manager links — linked={}, notFound={}", linked, notFound);
    }

    // ── Sync state tracking ────────────────────────────────────────────────

    private SfSyncState beginSyncState(String type) {
        SfSyncState state = SfSyncState.builder()
                .syncType(type)
                .startedAt(LocalDateTime.now(ZoneOffset.UTC))
                .status("RUNNING")
                .build();
        return syncStateRepo.save(state);
    }

    private void completeSyncState(SfSyncState state, DirectorySyncResultDto result,
                                   String status, String deltaToken) {
        state.setStatus(status);
        state.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
        if (result != null) {
            state.setUsersProcessed(result.usersProcessed());
            state.setUsersCreated(result.usersCreated());
            state.setUsersUpdated(result.usersUpdated());
            state.setUsersDeactivated(result.usersDeactivated());
            state.setOrgUnitsProcessed(result.orgUnitsProcessed());
            state.setOrgUnitsCreated(result.orgUnitsCreated());
            state.setOrgUnitsUpdated(result.orgUnitsUpdated());
            state.setErrorCount(result.errors());
        }
        if (deltaToken != null) state.setLastDeltaToken(deltaToken);
        syncStateRepo.save(state);
    }

    // ── Internal result types ──────────────────────────────────────────────

    record OrgTreeResult(
            int processed, int created, int updated,
            OrganizationalUnit unassigned,
            Map<String, OrganizationalUnit> cache
    ) {}

    record UserSyncResult(int created, int updated, int deactivated, int errors) {}
}
