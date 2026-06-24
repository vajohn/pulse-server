package com.edge.pulse.data.models.psychometric;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Latest value + trend per (user, scale) (§12, D1). The only mutable capability table. */
@Entity
@Table(name = "capability_profile_current")
@IdClass(CapabilityProfileCurrentId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CapabilityProfileCurrent {

    @Id @Column(name = "user_id", nullable = false)  private UUID userId;
    @Id @Column(name = "scale_id", nullable = false) private UUID scaleId;

    @Column(name = "test_id", nullable = false)         private UUID testId;
    @Column(name = "latest_result_id", nullable = false) private UUID latestResultId;

    @Column(name = "z_score", precision = 6, scale = 3) private BigDecimal zScore;
    @Column(name = "t_score", precision = 6, scale = 2) private BigDecimal tScore;
    @Column(name = "sten_score", precision = 4, scale = 2) private BigDecimal stenScore;
    @Column(precision = 5, scale = 2) private BigDecimal percentile;
    @Column(name = "norm_table_version_id") private UUID normTableVersionId;
    @Column(name = "scored_at", nullable = false) private LocalDateTime scoredAt;

    @Column(name = "prev_sten_score", precision = 4, scale = 2) private BigDecimal prevStenScore;
    @Column(name = "prev_scored_at") private LocalDateTime prevScoredAt;
    @Column(name = "prev_norm_version_id") private UUID prevNormVersionId;

    /** latest.sten − prev.sten; NULL when there is no prior administration (D1). */
    @Column(name = "sten_delta", precision = 5, scale = 2) private BigDecimal stenDelta;

    /** True when the latest norm version differs from the previous one (D5 boundary flag). */
    @Column(name = "norm_changed", nullable = false) @Builder.Default
    private boolean normChanged = false;

    @Column(name = "n_administrations", nullable = false) @Builder.Default
    private int nAdministrations = 1;

    @Column(name = "updated_at", nullable = false) @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
