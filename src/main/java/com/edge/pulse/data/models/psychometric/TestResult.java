package com.edge.pulse.data.models.psychometric;

import com.edge.pulse.data.enums.ResultState;
import com.edge.pulse.data.enums.TestResultStatus;
import com.edge.pulse.data.enums.ValidityStatus;
import com.edge.pulse.data.models.ResponseSession;
import com.edge.pulse.data.models.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "test_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"test", "session", "scoringKeyVersion", "normTableVersion", "reviewedBy"})
public class TestResult {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_id", nullable = false)
    private PsychometricTest test;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private ResponseSession session;

    /**
     * The exact scoring key used at scoring time — permanently recorded.
     * NULL only if scored before any key was published (edge case).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scoring_key_version_id")
    private ScoringKeyVersion scoringKeyVersion;

    /**
     * The exact norm table used at scoring time — permanently recorded.
     * NULL if no VALIDATED norm table existed at scoring time (status = PENDING).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "norm_table_version_id")
    private NormTableVersion normTableVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TestResultStatus status = TestResultStatus.PENDING;

    private LocalDateTime scoredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    private LocalDateTime reviewedAt;

    @Column(columnDefinition = "TEXT")
    private String reviewNotes;

    /** Focus-loss events reported by the client and confirmed server-side. */
    @Column(name = "focus_loss_count", nullable = false)
    @Builder.Default
    private int focusLossCount = 0;

    /** Lifecycle state for this result (computed by scoring engine; gated in later plans). */
    @Enumerated(EnumType.STRING)
    @Column(name = "result_state", nullable = false)
    @Builder.Default
    private ResultState resultState = ResultState.FINAL;

    /** Validity screening outcome; NULL until a validity scale has been scored. */
    @Enumerated(EnumType.STRING)
    @Column(name = "validity_status")
    private ValidityStatus validityStatus;
}
