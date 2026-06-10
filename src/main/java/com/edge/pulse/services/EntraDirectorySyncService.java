package com.edge.pulse.services;

import com.edge.pulse.data.dto.DirectorySyncResultDto;
import com.edge.pulse.data.dto.GraphGroup;
import com.edge.pulse.data.dto.GraphUserProfile;
import com.edge.pulse.data.enums.OrgLevel;
import com.edge.pulse.data.models.OrganizationalUnit;
import com.edge.pulse.data.models.User;
import com.edge.pulse.repositories.OrganizationalUnitRepository;
import com.edge.pulse.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class EntraDirectorySyncService {

    private final MicrosoftGraphService graphService;
    private final UserProvisioningService userProvisioningService;
    private final OrganizationalUnitRepository orgUnitRepo;
    private final UserRepository userRepo;

    @Value("${pulse.entra.sync.stale-hours:23}")
    private int staleHours;

    public DirectorySyncResultDto syncDirectory() {
        log.info("EntraSync: acquiring app-only token");
        String appToken = graphService.acquireAppOnlyToken();

        DirectorySyncResultDto groupResult = syncOrgUnitsFromGroups(appToken);
        DirectorySyncResultDto userResult  = syncUsers(appToken);

        return merge(groupResult, userResult);
    }

    // package-private for unit tests
    DirectorySyncResultDto syncOrgUnitsFromGroups(String appToken) {
        List<GraphGroup> groups = graphService.fetchAllGroups(appToken);
        int created = 0, updated = 0;

        for (GraphGroup group : groups) {
            Optional<OrganizationalUnit> existing = findOrgUnitForGroup(group);
            if (existing.isPresent()) {
                OrganizationalUnit ou = existing.get();
                if (!group.displayName().equals(ou.getOrgUnitName())) {
                    ou.setOrgUnitName(group.displayName());
                    orgUnitRepo.save(ou);
                    updated++;
                }
            } else {
                OrganizationalUnit ou = new OrganizationalUnit();
                ou.setEntraGroupId(group.id());
                ou.setOrgUnitName(group.displayName());
                ou.setOrgUnitCode(group.displayName());
                ou.setOrgLevel(OrgLevel.ORG_UNIT);
                ou.setPath("/" + group.displayName());
                ou.setDepth(1);
                ou.setActive(true);
                orgUnitRepo.save(ou);
                created++;
                log.info("EntraSync: created org unit '{}' from group {}", group.displayName(), group.id());
            }
        }

        log.info("EntraSync: org units — processed={}, created={}, updated={}", groups.size(), created, updated);
        return new DirectorySyncResultDto(0, 0, 0, 0, groups.size(), created, updated, 0,
                LocalDateTime.now(ZoneOffset.UTC));
    }

    DirectorySyncResultDto syncUsers(String appToken) {
        List<GraphUserProfile> allUsers = graphService.fetchAllUsers(appToken);
        LocalDateTime staleThreshold = LocalDateTime.now(ZoneOffset.UTC).minusHours(staleHours);
        int created = 0, updated = 0, deactivated = 0, errors = 0;

        for (GraphUserProfile gUser : allUsers) {
            try {
                String email = gUser.effectiveEmail();
                if (email == null || email.isBlank()) {
                    log.debug("EntraSync: skipping user {} — no mail or userPrincipalName", gUser.id());
                    continue;
                }

                Optional<User> existing = userRepo.findByAzureAdId(gUser.id());

                // Skip recently synced users
                if (existing.isPresent()
                        && existing.get().getEntraLastSyncedAt() != null
                        && existing.get().getEntraLastSyncedAt().isAfter(staleThreshold)) {
                    continue;
                }

                boolean isNew = existing.isEmpty();

                Optional<GraphUserProfile> manager = graphService.fetchUserManagerById(appToken, gUser.id());
                String managerAzureAdId = manager.map(GraphUserProfile::id).orElse(null);

                userProvisioningService.provisionOrUpdateUser(
                        gUser.id(), email, gUser.displayName(),
                        gUser.jobTitle(),
                        Set.of(), Set.of(), Set.of(), Set.of(),
                        gUser.department(), gUser.employeeId(), managerAzureAdId
                );

                if (Boolean.FALSE.equals(gUser.accountEnabled())) {
                    userRepo.findByAzureAdId(gUser.id()).ifPresent(u -> {
                        u.setActive(false);
                        userRepo.save(u);
                    });
                    deactivated++;
                } else if (isNew) {
                    created++;
                } else {
                    updated++;
                }
            } catch (Exception e) {
                log.warn("EntraSync: failed to sync user {} — {}", gUser.id(), e.getMessage());
                errors++;
            }
        }

        log.info("EntraSync: users — processed={}, created={}, updated={}, deactivated={}, errors={}",
                allUsers.size(), created, updated, deactivated, errors);
        return new DirectorySyncResultDto(allUsers.size(), created, updated, deactivated, 0, 0, 0, errors,
                LocalDateTime.now(ZoneOffset.UTC));
    }

    public DirectorySyncResultDto getStatus() {
        long total = userRepo.countByActiveTrue();
        long stale = userRepo.countByEntraLastSyncedAtBeforeOrEntraLastSyncedAtIsNull(
                LocalDateTime.now(ZoneOffset.UTC).minusHours(staleHours));
        LocalDateTime lastSync = userRepo.findMaxEntraLastSyncedAt().orElse(null);
        log.debug("EntraSync status: activeUsers={}, staleUsers={}, lastSync={}", total, stale, lastSync);
        return new DirectorySyncResultDto((int) total, 0, 0, 0, 0, 0, 0, (int) stale, lastSync);
    }

    private Optional<OrganizationalUnit> findOrgUnitForGroup(GraphGroup group) {
        return orgUnitRepo.findByEntraGroupId(group.id())
                .or(() -> orgUnitRepo.findByOrgUnitCode(group.displayName()));
    }

    private DirectorySyncResultDto merge(DirectorySyncResultDto groups, DirectorySyncResultDto users) {
        return new DirectorySyncResultDto(
                users.usersProcessed(),
                users.usersCreated(),
                users.usersUpdated(),
                users.usersDeactivated(),
                groups.orgUnitsProcessed(),
                groups.orgUnitsCreated(),
                groups.orgUnitsUpdated(),
                users.errors() + groups.errors(),
                users.syncedAt()
        );
    }
}
