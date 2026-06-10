package com.edge.pulse.services;

import com.edge.pulse.data.enums.OrgLevel;
import com.edge.pulse.data.models.*;
import com.edge.pulse.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProvisioningService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final TeamRepository teamRepository;
    private final GroupRepository groupRepository;
    private final TitleRepository titleRepository;
    private final OrganizationalUnitRepository orgUnitRepository;
    private final UserOrgUnitRepository userOrgUnitRepository;
    private final FormCacheService formCacheService;

    // DEV ONLY — set via pulse.dev.auto-admin=true in application-local.yaml.
    // Never enabled outside the local Spring profile (ProfileGuard enforces this).
    @Value("${pulse.dev.auto-admin:false}")
    private boolean autoAdmin;

    @Transactional
    public User provisionOrUpdateUser(String azureAdId, String email, String displayName, String titleName,
                                      Set<String> roleNames, Set<String> permissionNames, Set<String> teamNames, Set<String> groupNames) {
        User user = userRepository.findByAzureAdId(azureAdId)
                .or(() -> {
                    // Fallback: user exists with this email but a different/null azureAdId (e.g. seeded data)
                    Optional<User> byEmail = userRepository.findByEmail(email);
                    byEmail.ifPresent(u -> {
                        u.setAzureAdId(azureAdId);
                        log.info("Linked azureAdId {} to existing user {} found by email", azureAdId, email);
                    });
                    return byEmail;
                })
                .orElse(null);
        boolean changed = false;
        if (user == null) {
            user = new User();
            user.setAzureAdId(azureAdId);
            user.setEmail(email);
            user.setDisplayName(displayName);
            changed = true;
        } else {
            if (!Objects.equals(user.getEmail(), email)) { user.setEmail(email); changed = true; }
            if (!Objects.equals(user.getDisplayName(), displayName)) { user.setDisplayName(displayName); changed = true; }
        }
        // Title
        if (titleName != null) {
            Title title = titleRepository.findByName(titleName).orElseGet(() -> titleRepository.save(Title.builder().name(titleName).build()));
            if (!Objects.equals(user.getTitle(), title)) { user.setTitle(title); changed = true; }
        }
        // Roles
        Set<Role> roles = new HashSet<>();
        for (String r : roleNames) {
            roles.add(roleRepository.findByName(r).orElseGet(() -> roleRepository.save(Role.builder().name(r).build())));
        }
        // DEV ONLY — active only when pulse.dev.auto-admin=true (application-local.yaml).
        // The 'local' profile guard in ProfileGuard prevents this from activating in
        // non-local environments. Hardcoded list: do NOT replace with a dynamic DB query —
        // dynamic loading would silently grant any future role created at runtime.
        if (autoAdmin) {
            List<String> devRoles = List.of(
                    // Tier 1 — participation (default baseline)
                    "SURVEY_RESPONDENT", "ASSESSMENT_CANDIDATE", "PEER_NOMINATOR",
                    "BROADCAST_VIEWER", "SPARK_VOTER",
                    // Tier 2 — elevated access
                    "SCOPE_TEAM_LEAD", "SCOPE_ENTITY_LEAD",
                    "SURVEY_RESULT_VIEWER", "ASSESSMENT_RESULT_VIEWER",
                    // Tier 3 — HR capability
                    "FORM_AUTHOR", "FORM_ASSIGNER", "SURVEY_ANALYST", "SURVEY_TEXT_ANALYST",
                    "ASSESSMENT_ADMIN", "DIRECTORY_ADMIN", "SPARK_ADMIN", "BROADCAST_AUTHOR",
                    // Tier 4 — bootstrap
                    "ROLE_ADMINISTRATOR"
            );
            for (String roleName : devRoles) {
                roleRepository.findByName(roleName).ifPresent(r -> {
                    roles.add(r);
                    log.warn("DEV auto-admin: granting {} to {}", r.getName(), email);
                });
            }
        }
        if (!Objects.equals(user.getRoles(), roles)) { user.setRoles(roles); changed = true; }
        // Permissions
        Set<Permission> permissions = new HashSet<>();
        for (String p : permissionNames) {
            permissions.add(permissionRepository.findByName(p).orElseGet(() -> permissionRepository.save(Permission.builder().name(p).build())));
        }
        if (!Objects.equals(user.getPermissions(), permissions)) { user.setPermissions(permissions); changed = true; }
        // Teams
        Set<Team> teams = new HashSet<>();
        for (String t : teamNames) {
            teams.add(teamRepository.findByName(t).orElseGet(() -> teamRepository.save(Team.builder().name(t).build())));
        }
        if (!Objects.equals(user.getTeams(), teams)) { user.setTeams(teams); changed = true; }
        // Groups
        Set<Group> groups = new HashSet<>();
        for (String g : groupNames) {
            groups.add(groupRepository.findByName(g).orElseGet(() -> groupRepository.save(Group.builder().name(g).build())));
        }
        if (!Objects.equals(user.getGroups(), groups)) { user.setGroups(groups); changed = true; }
        // Auto-assign unassigned org unit if none set
        boolean orgUnitChanged = false;
        boolean assignedToUnassigned = false;
        if (user.getOrgUnit() == null) {
            OrganizationalUnit unassignedUnit = orgUnitRepository.findByOrgUnitCode("UNASSIGNED")
                    .orElseGet(() -> orgUnitRepository.save(OrganizationalUnit.builder()
                            .orgUnitName("Unassigned")
                            .orgUnitCode("UNASSIGNED")
                            .orgLevel(OrgLevel.ORG_UNIT)
                            .path("/UNASSIGNED")
                            .depth(0)
                            .active(true)
                            .build()));
            user.setOrgUnit(unassignedUnit);
            assignedToUnassigned = true;
            orgUnitChanged = true;
            changed = true;
            log.info("Auto-assigned unassigned org unit to user {}", user.getEmail());
        }
        if (changed) {
            user = userRepository.save(user);
            log.info("User {} ({}) provisioned/updated", user.getEmail(), user.getAzureAdId());
        } else {
            log.debug("User {} ({}) unchanged", user.getEmail(), user.getAzureAdId());
        }
        // Create user_org_unit row so the user is fully linked to their org unit
        if (assignedToUnassigned) {
            assignUserToOrgUnit(user, user.getOrgUnit(), false);
        }
        // Org unit change invalidates the user's visible assignment set
        if (orgUnitChanged && user.getId() != null) {
            formCacheService.evict(FormCacheService.userAssignmentsKey(user.getId()));
        }
        return user;
    }

    @Transactional
    public User provisionOrUpdateUser(String azureAdId, String email, String displayName, String titleName,
                                      Set<String> roleNames, Set<String> permissionNames,
                                      Set<String> teamNames, Set<String> groupNames,
                                      String department, String employeeId, String managerAzureAdId) {
        User user = provisionOrUpdateUser(azureAdId, email, displayName, titleName,
                roleNames, permissionNames, teamNames, groupNames);

        boolean changed = false;

        if (department != null && !Objects.equals(user.getDepartment(), department)) {
            user.setDepartment(department);
            changed = true;
        }

        if (employeeId != null && !Objects.equals(user.getEmployeeId(), employeeId)) {
            user.setEmployeeId(employeeId);
            changed = true;
        }

        if (managerAzureAdId != null) {
            Optional<User> managerOpt = userRepository.findByAzureAdId(managerAzureAdId);
            if (managerOpt.isPresent() && !Objects.equals(user.getManager(), managerOpt.get())) {
                user.setManager(managerOpt.get());
                changed = true;
                log.info("Linked manager {} for user {}", managerOpt.get().getEmail(), user.getEmail());
            }
        }

        // If department is available and user still has the UNASSIGNED org unit, try to match
        boolean enrichedOrgUnitChanged = false;
        if (department != null && user.getOrgUnit() != null
                && "UNASSIGNED".equals(user.getOrgUnit().getOrgUnitCode())) {
            Optional<OrganizationalUnit> unitOpt = orgUnitRepository.findByOrgUnitCode(department);
            if (unitOpt.isPresent()) {
                OrganizationalUnit previousUnit = user.getOrgUnit();
                user.setOrgUnit(unitOpt.get());
                changed = true;
                enrichedOrgUnitChanged = true;
                log.info("Matched org unit {} for user {} from department", department, user.getEmail());
                // Remove old UNASSIGNED link and create the new one
                removeUserFromOrgUnit(user, previousUnit);
                assignUserToOrgUnit(user, unitOpt.get(), false);
            }
        }

        user.setEntraLastSyncedAt(LocalDateTime.now());
        user = userRepository.save(user);
        log.info("Graph enrichment applied for user {}", user.getEmail());

        if (enrichedOrgUnitChanged) {
            formCacheService.evict(FormCacheService.userAssignmentsKey(user.getId()));
        }

        return user;
    }

    private void assignUserToOrgUnit(User user, OrganizationalUnit orgUnit, boolean isLeader) {
        UserOrgUnitId id = new UserOrgUnitId(user.getId(), orgUnit.getId());
        if (userOrgUnitRepository.existsById(id)) {
            return;
        }
        UserOrgUnit link = UserOrgUnit.builder()
                .user(user)
                .orgUnit(orgUnit)
                .isLeader(isLeader)
                .assignedAt(LocalDateTime.now())
                .build();
        userOrgUnitRepository.save(link);
        log.info("Created user_org_unit link for user {} → {}", user.getEmail(), orgUnit.getOrgUnitCode());
    }

    private void removeUserFromOrgUnit(User user, OrganizationalUnit orgUnit) {
        UserOrgUnitId id = new UserOrgUnitId(user.getId(), orgUnit.getId());
        if (userOrgUnitRepository.existsById(id)) {
            userOrgUnitRepository.deleteById(id);
            log.info("Removed user_org_unit link for user {} → {}", user.getEmail(), orgUnit.getOrgUnitCode());
        }
    }
}
