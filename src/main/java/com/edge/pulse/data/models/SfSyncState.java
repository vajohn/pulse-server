package com.edge.pulse.data.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sf_sync_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SfSyncState {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    /** FULL or DELTA */
    @Column(name = "sync_type", nullable = false)
    private String syncType;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** RUNNING, SUCCESS, FAILED */
    @Column(nullable = false)
    @Builder.Default
    private String status = "RUNNING";

    @Column(name = "users_processed", nullable = false)
    @Builder.Default
    private int usersProcessed = 0;

    @Column(name = "users_created", nullable = false)
    @Builder.Default
    private int usersCreated = 0;

    @Column(name = "users_updated", nullable = false)
    @Builder.Default
    private int usersUpdated = 0;

    @Column(name = "users_deactivated", nullable = false)
    @Builder.Default
    private int usersDeactivated = 0;

    @Column(name = "org_units_processed", nullable = false)
    @Builder.Default
    private int orgUnitsProcessed = 0;

    @Column(name = "org_units_created", nullable = false)
    @Builder.Default
    private int orgUnitsCreated = 0;

    @Column(name = "org_units_updated", nullable = false)
    @Builder.Default
    private int orgUnitsUpdated = 0;

    @Column(name = "error_count", nullable = false)
    @Builder.Default
    private int errorCount = 0;

    /** OData delta-link token for incremental sync. */
    @Column(name = "last_delta_token", length = 1024)
    private String lastDeltaToken;
}
