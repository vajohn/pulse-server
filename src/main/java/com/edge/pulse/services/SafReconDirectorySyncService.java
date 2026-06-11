package com.edge.pulse.services;

import com.edge.pulse.configs.SafReconProperties;
import com.edge.pulse.data.dto.DirectorySyncResultDto;
import com.edge.pulse.data.dto.safrecon.SafReconEmployee;
import com.edge.pulse.data.dto.safrecon.SafReconEmployeePage;
import com.edge.pulse.data.dto.safrecon.SafReconOrgUnit;
import com.edge.pulse.data.enums.OrgLevel;
import com.edge.pulse.data.models.*;
import com.edge.pulse.repositories.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pulls the full employee/org set from saf-recon-server and upserts it into Pulse's
 * users / organizational_units / user_sf_profile tables. Pull-only (no delta): idempotent
 * upsert + guarded deactivate-missing. Replaces the retired SAP SF and Entra sync paths.
 */
@Service
@Slf4j
public class SafReconDirectorySyncService {

    private static final String SYNC_SOURCE = "SAF_RECON";

    private final SafReconClient client;
    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final UserSfProfileRepository sfProfileRepo;
    private final OrganizationalUnitRepository orgUnitRepo;
    private final TitleRepository titleRepo;
    private final SfSyncStateRepository syncStateRepo;
    private final FormCacheService cacheService;
    private final SafReconProperties props;

    public SafReconDirectorySyncService(SafReconClient client, UserRepository userRepo,
            RoleRepository roleRepo, UserSfProfileRepository sfProfileRepo,
            OrganizationalUnitRepository orgUnitRepo, TitleRepository titleRepo,
            SfSyncStateRepository syncStateRepo, FormCacheService cacheService,
            SafReconProperties props) {
        this.client = client;
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.sfProfileRepo = sfProfileRepo;
        this.orgUnitRepo = orgUnitRepo;
        this.titleRepo = titleRepo;
        this.syncStateRepo = syncStateRepo;
        this.cacheService = cacheService;
        this.props = props;
    }

    public DirectorySyncResultDto fullSync() {
        if (!client.isConfigured()) {
            throw new IllegalStateException("saf-recon client is not configured");
        }
        SfSyncState state = begin("SAF_RECON_FULL");
        int orgCreated = 0, orgUpdated = 0, usersCreated = 0, usersUpdated = 0, usersDeactivated = 0, errors = 0, skipped = 0;
        try {
            // Pass 1: org tree
            List<SafReconOrgUnit> safUnits = client.fetchOrgUnits();
            Map<UUID, OrganizationalUnit> ouBySafId = new HashMap<>();
            // create/update each, then link parents in a second loop (input is depth-sorted)
            for (SafReconOrgUnit s : safUnits) {
                OrganizationalUnit ou = orgUnitRepo.findByOrgUnitCode(s.sfCode()).orElse(null);
                boolean isNew = ou == null;
                if (isNew) { ou = new OrganizationalUnit(); ou.setOrgUnitCode(s.sfCode()); }
                ou.setOrgUnitName(s.name());
                ou.setOrgLevel(mapLevel(s.level()));
                ou.setPath(s.path() != null ? s.path() : "");
                ou.setDepth(s.depth());
                ou.setCompanyCode(s.companyCode());
                ou.setSyncSource(SYNC_SOURCE);
                ou.setActive(true);
                ou = orgUnitRepo.save(ou);
                ouBySafId.put(s.id(), ou);
                if (isNew) orgCreated++; else orgUpdated++;
            }
            for (SafReconOrgUnit s : safUnits) {
                if (s.parentId() != null) {
                    OrganizationalUnit child = ouBySafId.get(s.id());
                    OrganizationalUnit parent = ouBySafId.get(s.parentId());
                    if (child != null && parent != null) {
                        child.setParent(parent);
                        orgUnitRepo.save(child);
                    }
                }
            }

            // Pass 2: employees (full pull, paged)
            List<SafReconEmployee> employees = fetchAllEmployees();

            // §4e — duplicate work-emails must not pollute Pulse. Skip ALL records sharing an email.
            Map<String, Long> emailCounts = new HashMap<>();
            for (SafReconEmployee e : employees) {
                if (e.workEmail() != null && !e.workEmail().isBlank()) {
                    emailCounts.merge(e.workEmail().toLowerCase(), 1L, Long::sum);
                }
            }
            Set<String> duplicateEmails = emailCounts.entrySet().stream()
                    .filter(en -> en.getValue() > 1).map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            if (!duplicateEmails.isEmpty()) {
                log.warn("saf-recon returned {} duplicated work-emails — skipping all their records: {}",
                        duplicateEmails.size(), duplicateEmails);
            }

            // §4e — manager signal: any saf id referenced as another employee's managerId.
            Set<UUID> managerSafIds = new HashSet<>();
            for (SafReconEmployee e : employees) {
                if (e.managerId() != null) managerSafIds.add(e.managerId());
            }

            Map<UUID, User> userBySafEmpId = new HashMap<>();
            Set<String> seenEmails = new HashSet<>();
            for (SafReconEmployee e : employees) {
                try {
                    if (e.workEmail() == null || e.workEmail().isBlank()) { errors++; continue; }
                    // Skip duplicate emails entirely — no insert, no enrichment, no overwrite.
                    if (duplicateEmails.contains(e.workEmail().toLowerCase())) { skipped++; continue; }
                    User u = userRepo.findWithRolesByEmail(e.workEmail()).orElse(null);
                    boolean isNew = u == null;
                    if (isNew) { u = new User(); u.setEmail(e.workEmail()); }
                    u.setDisplayName(deriveName(e));
                    u.setSfUserId(e.sfUserId());
                    u.setEmployeeId(e.sfUserId());
                    u.setDepartment(e.department());
                    u.setCompanyCode(e.companyCode());
                    u.setActive("ACTIVE".equalsIgnoreCase(e.status()));
                    if (e.jobTitle() != null && !e.jobTitle().isBlank()) {
                        u.setTitle(resolveTitle(e.jobTitle()));
                    }
                    if (e.orgUnitId() != null) {
                        u.setOrgUnit(ouBySafId.get(e.orgUnitId()));
                    }
                    // §4e — manager-toggle role reconciliation (mutates ONLY EMPLOYEE/MANAGER).
                    // Signal = managerId-reference only (precise; no job-title heuristic / no IC false-positives).
                    boolean isManager = managerSafIds.contains(e.id());
                    reconcileRoles(u, isManager);
                    User saved = userRepo.save(u);
                    upsertProfile(saved, e);
                    userBySafEmpId.put(e.id(), saved);
                    seenEmails.add(e.workEmail().toLowerCase());
                    cacheService.evict(FormCacheService.userAssignmentsKey(saved.getId()));
                    if (isNew) usersCreated++; else usersUpdated++;
                } catch (Exception ex) {
                    log.warn("saf-recon user upsert failed for {}: {}", e.workEmail(), ex.getMessage());
                    errors++;
                }
            }

            // Manager links
            for (SafReconEmployee e : employees) {
                if (e.managerId() != null) {
                    User u = userBySafEmpId.get(e.id());
                    User mgr = userBySafEmpId.get(e.managerId());
                    if (u != null && mgr != null && !mgr.getId().equals(u.getId())) {
                        u.setManager(mgr);
                        userRepo.save(u);
                    }
                }
            }

            // Deactivate-missing (guarded). Never deactivate an ambiguous (duplicate-email) user.
            if (props.getDeactivateFloor() <= 0 || employees.size() >= props.getDeactivateFloor()) {
                for (User existing : userRepo.findAll()) {
                    if (existing.isActive() && existing.getEmail() != null
                            && !seenEmails.contains(existing.getEmail().toLowerCase())
                            && !duplicateEmails.contains(existing.getEmail().toLowerCase())) {
                        existing.setActive(false);
                        userRepo.save(existing);
                        usersDeactivated++;
                    }
                }
            } else {
                log.warn("saf-recon returned {} employees (< floor {}) — skipping deactivation",
                        employees.size(), props.getDeactivateFloor());
            }

            if (skipped > 0) {
                log.warn("saf-recon sync skipped {} records due to duplicate work-emails", skipped);
            }
            // `skipped` is rolled into the errors total (records not synced due to upstream DQ).
            DirectorySyncResultDto result = new DirectorySyncResultDto(
                    employees.size(), usersCreated, usersUpdated, usersDeactivated,
                    safUnits.size(), orgCreated, orgUpdated, errors + skipped, LocalDateTime.now());
            complete(state, result, "COMPLETED");
            return result;
        } catch (Exception e) {
            log.error("saf-recon full sync failed: {}", e.getMessage(), e);
            state.setStatus("FAILED");
            state.setCompletedAt(LocalDateTime.now());
            state.setErrorCount(errors + 1);
            syncStateRepo.save(state);
            throw e;
        }
    }

    private List<SafReconEmployee> fetchAllEmployees() {
        List<SafReconEmployee> all = new ArrayList<>();
        int page = 0;
        while (true) {
            SafReconEmployeePage p = client.fetchEmployeesPage(page, props.getPageSize());
            all.addAll(p.content());
            if (p.content().size() < props.getPageSize()) break;
            page++;
        }
        return all;
    }

    /**
     * §4e manager-toggle reconciliation. Mutates ONLY EMPLOYEE/MANAGER membership; every other
     * role (HR_FULL_CRUD, ASSESSMENT_ADMIN, ROLE_ALL, approval-workflow grants) is left untouched.
     */
    private void reconcileRoles(User user, boolean isManager) {
        Set<Role> roles = user.getRoles() != null ? user.getRoles() : new HashSet<>();
        // EMPLOYEE baseline
        if (roles.stream().noneMatch(r -> "EMPLOYEE".equals(r.getName()))) {
            roleRepo.findByName("EMPLOYEE").ifPresent(roles::add);
        }
        boolean hasManager = roles.stream().anyMatch(r -> "MANAGER".equals(r.getName()));
        if (isManager && !hasManager) {
            roleRepo.findByName("MANAGER").ifPresent(roles::add);
        } else if (!isManager && hasManager) {
            roles.removeIf(r -> "MANAGER".equals(r.getName()));
        }
        user.setRoles(roles);
    }

    private OrgLevel mapLevel(String level) {
        if (level == null) return OrgLevel.ORG_UNIT;
        try { return OrgLevel.valueOf(level); } catch (IllegalArgumentException ex) { return OrgLevel.ORG_UNIT; }
    }

    private String deriveName(SafReconEmployee e) {
        if (e.fullName() != null && !e.fullName().isBlank()) return e.fullName().trim();
        String concat = ((e.firstName() == null ? "" : e.firstName()) + " "
                + (e.lastName() == null ? "" : e.lastName())).trim();
        return concat.isBlank() ? e.workEmail() : concat;
    }

    private Title resolveTitle(String name) {
        return titleRepo.findByName(name).orElseGet(() -> {
            Title t = new Title();
            t.setName(name);
            return titleRepo.save(t);
        });
    }

    private void upsertProfile(User user, SafReconEmployee e) {
        UserSfProfile p = sfProfileRepo.findById(user.getId()).orElseGet(() -> {
            UserSfProfile np = new UserSfProfile();
            np.setUser(user);
            return np;
        });
        p.setSfSyncedAt(LocalDateTime.now());
        p.setSfHireDate(e.hireDate());
        p.setSfEmployeeType(e.employeeType());
        p.setSfFunction(e.jobFunction());
        sfProfileRepo.save(p);
    }

    private SfSyncState begin(String type) {
        SfSyncState s = new SfSyncState();
        s.setSyncType(type);
        s.setStartedAt(LocalDateTime.now());
        s.setStatus("RUNNING");
        return syncStateRepo.save(s);
    }

    private void complete(SfSyncState s, DirectorySyncResultDto r, String status) {
        s.setStatus(status);
        s.setCompletedAt(LocalDateTime.now());
        s.setUsersProcessed(r.usersProcessed());
        s.setUsersCreated(r.usersCreated());
        s.setUsersUpdated(r.usersUpdated());
        s.setUsersDeactivated(r.usersDeactivated());
        s.setOrgUnitsProcessed(r.orgUnitsProcessed());
        s.setOrgUnitsCreated(r.orgUnitsCreated());
        s.setOrgUnitsUpdated(r.orgUnitsUpdated());
        s.setErrorCount(r.errors());
        syncStateRepo.save(s);
    }

    public DirectorySyncResultDto getStatus() {
        return syncStateRepo.findAll().stream()
                .max(Comparator.comparing(SfSyncState::getStartedAt))
                .map(s -> new DirectorySyncResultDto(s.getUsersProcessed(), s.getUsersCreated(),
                        s.getUsersUpdated(), s.getUsersDeactivated(), s.getOrgUnitsProcessed(),
                        s.getOrgUnitsCreated(), s.getOrgUnitsUpdated(), s.getErrorCount(), s.getCompletedAt()))
                .orElse(new DirectorySyncResultDto(0, 0, 0, 0, 0, 0, 0, 0, null));
    }
}
