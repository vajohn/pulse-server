package com.edge.pulse.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduled full sync from saf-recon. No-ops when the client is not configured. */
@Component
@Slf4j
@RequiredArgsConstructor
public class SafReconDirectorySyncJob {

    private final SafReconDirectorySyncService syncService;
    private final SafReconClient client;

    @Scheduled(cron = "${pulse.saf-recon.sync.full-cron:0 0 1 * * *}")
    public void runFullSync() {
        if (!client.isConfigured()) return;
        log.info("SafReconSyncJob [FULL]: scheduled run starting");
        try {
            syncService.fullSync();
        } catch (Exception e) {
            log.error("SafReconSyncJob [FULL]: scheduled run failed — {}", e.getMessage(), e);
        }
    }
}
