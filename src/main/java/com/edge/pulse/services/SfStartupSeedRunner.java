package com.edge.pulse.services;

import com.edge.pulse.data.dto.DirectorySyncResultDto;
import com.edge.pulse.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Triggers a full SF SuccessFactors sync on startup when the database is empty.
 *
 * <p>Enabled by setting {@code pulse.sf.seed-on-startup=true} (application-local.yaml).
 * Restricted to the {@code local} Spring profile — never runs in staging or production,
 * where initial data is loaded via the scheduled sync job instead.
 *
 * <p>Guard logic:
 * <ul>
 *   <li>If {@code users} table is empty → full sync runs via the SOCKS5 proxy to SF.</li>
 *   <li>If one or more users already exist → sync is skipped. Safe to leave the property
 *       enabled across restarts without re-seeding.</li>
 * </ul>
 *
 * <p>Fires on {@link ApplicationReadyEvent} so Flyway migrations and JPA context are
 * fully initialised before the sync begins.
 */
@Component
@Profile("local")
@RequiredArgsConstructor
@Slf4j
public class SfStartupSeedRunner {

    private final SfDirectorySyncService syncService;
    private final UserRepository userRepository;

    @Value("${pulse.sf.seed-on-startup:false}")
    private boolean seedOnStartup;

    @EventListener(ApplicationReadyEvent.class)
    public void seedIfEmpty() {
        if (!seedOnStartup) {
            return;
        }

        long userCount = userRepository.count();
        if (userCount > 0) {
            log.info("SfSeed: seed-on-startup=true but {} user(s) already in DB — skipping SF fetch",
                    userCount);
            return;
        }

        log.info("SfSeed: seed-on-startup=true and DB is empty — triggering full SF sync via proxy");
        try {
            DirectorySyncResultDto result = syncService.fullSync();
            log.info("SfSeed: initial seed complete — users={} orgUnits={} errors={}",
                    result.usersProcessed(), result.orgUnitsProcessed(), result.errors());
        } catch (Exception e) {
            log.error("SfSeed: initial seed failed — check SOCKS5 proxy and SF credentials. {}",
                    e.getMessage(), e);
        }
    }
}
