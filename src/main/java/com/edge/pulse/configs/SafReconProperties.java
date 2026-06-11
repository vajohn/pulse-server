package com.edge.pulse.configs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * saf-recon-server integration config. saf-recon is the sole upstream for employee/org data on k2.
 * Reached pod-to-pod, e.g. {@code http://saf-recon-server.tasdiq-dev.svc.cluster.local:8081}.
 * When {@code baseUrl}/{@code username}/{@code password} are blank the sync self-disables.
 */
@Configuration
@ConfigurationProperties(prefix = "saf-recon")
@Getter
@Setter
public class SafReconProperties {
    /** API root (no trailing slash). e.g. http://saf-recon-server.tasdiq-dev.svc.cluster.local:8081 */
    private String baseUrl;
    /** Service-account username configured in saf-recon's app.users (role ANALYST). */
    private String username;
    /** Service-account plaintext password (k8s secret — never defaulted). */
    private String password;
    /** Page size for the paged /employees pull. */
    private int pageSize = 200;
    /**
     * Deactivate-missing safety floor: if a full pull returns fewer than this many
     * employees, skip deactivating Pulse users absent from the set (guards against an
     * upstream glitch mass-deactivating everyone). 0 disables the guard.
     */
    private int deactivateFloor = 50;
}
