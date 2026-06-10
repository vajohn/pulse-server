package com.edge.pulse.services;

import com.edge.pulse.data.dto.DirectorySyncResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class EntraDirectorySyncJob {

    private final EntraDirectorySyncService syncService;

    @Scheduled(cron = "${pulse.entra.sync.cron:0 0 3 * * *}")
    public void runSync() {
        log.info("EntraSync: starting scheduled directory sync");
        DirectorySyncResultDto result = syncService.syncDirectory();
        log.info("EntraSync: completed — users={}, created={}, updated={}, deactivated={}, " +
                        "orgUnits={}, errors={}",
                result.usersProcessed(), result.usersCreated(), result.usersUpdated(),
                result.usersDeactivated(), result.orgUnitsProcessed(), result.errors());
    }
}
