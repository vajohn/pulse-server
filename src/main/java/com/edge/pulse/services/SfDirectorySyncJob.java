package com.edge.pulse.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled wrapper for {@link SfDirectorySyncService}.
 *
 * <p>Full sync runs nightly (configurable via {@code pulse.sf.sync.full-cron}).
 * Delta sync runs every 6 hours (configurable via {@code pulse.sf.sync.delta-cron}).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SfDirectorySyncJob {

    private final SfDirectorySyncService syncService;

    /** Full sync — nightly at 01:00 UTC. */
    @Scheduled(cron = "${pulse.sf.sync.full-cron:0 0 1 * * *}")
    public void runFullSync() {
        log.info("SfSyncJob [FULL]: scheduled run starting");
        try {
            syncService.fullSync();
        } catch (Exception e) {
            log.error("SfSyncJob [FULL]: scheduled run failed — {}", e.getMessage(), e);
        }
    }

    /** Delta sync — every 6 hours. */
    @Scheduled(cron = "${pulse.sf.sync.delta-cron:0 0 */6 * * *}")
    public void runDeltaSync() {
        log.info("SfSyncJob [DELTA]: scheduled run starting");
        try {
            syncService.deltaSync();
        } catch (Exception e) {
            log.error("SfSyncJob [DELTA]: scheduled run failed — {}", e.getMessage(), e);
        }
    }
}
