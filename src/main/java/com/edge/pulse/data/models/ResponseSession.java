package com.edge.pulse.data.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "response_session")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"form", "user", "anonIdentity"})
public class ResponseSession {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "form_id", nullable = false)
    private Form form;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anon_identity_id")
    private AnonIdentity anonIdentity;

    @Column(name = "is_anonymous", nullable = false)
    private boolean isAnonymous;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    /** UTC epoch millis recorded at session creation — server clock only, used for timer enforcement. */
    @Column(name = "server_start_epoch")
    private Long serverStartEpoch;

    /** Time limit in seconds copied from PsychometricTest. NULL for surveys and untimed tests. */
    @Column(name = "time_limit_secs")
    private Integer timeLimitSecs;

    /** Randomised item order for psychometric sessions — JSON array of question UUIDs. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "item_sequence", columnDefinition = "jsonb")
    private List<UUID> itemSequence;

    /**
     * Running count of app-background (focus-loss) events during this session.
     * Incremented by {@code POST /api/psychometric/sessions/{id}/focus-event}.
     * Copied to {@code test_result.focus_loss_count} at scoring time.
     */
    @Column(name = "focus_loss_count", nullable = false)
    @Builder.Default
    private int focusLossCount = 0;
}
