package com.edge.pulse.data.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Optional 1:1 extension of {@link User} holding SAP SuccessFactors metadata.
 *
 * <p>Only exists for users synced from SF. Keeps the hot {@code users} table
 * lean — auth, session, and assignment queries never need these columns.
 *
 * <p><b>Data retention:</b> {@code sf_hire_date} and {@code sf_employee_type} are
 * employment PII. Rows are deleted automatically via {@code ON DELETE CASCADE} when
 * the parent {@link User} is deleted. Active employee records are retained for the
 * duration of employment; inactive/alumni users should be deactivated in SF and
 * purged from Pulse per the HR data-retention schedule (TBD with compliance team).
 *
 * <p>Uses a shared-PK mapping: {@code user_sf_profile.user_id} is both the PK
 * and the FK to {@code users.id}.
 */
@Entity
@Table(name = "user_sf_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSfProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    /** Timestamp of the last successful SF sync for this user. */
    @Column(name = "sf_synced_at")
    private LocalDateTime sfSyncedAt;

    /** Hire date from SF {@code hireDate} field. */
    @Column(name = "sf_hire_date")
    private LocalDate sfHireDate;

    /** Employee type from SF {@code custom05}. */
    @Column(name = "sf_employee_type")
    private String sfEmployeeType;

    /** Job function from SF {@code custom08}. */
    @Column(name = "sf_function")
    private String sfFunction;
}
