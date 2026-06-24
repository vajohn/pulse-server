package com.edge.pulse.data.models.psychometric;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Append-only history row: one per scored leaf scale per result (§12, D5). Never updated. */
@Entity
@Table(name = "capability_score_history")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CapabilityScoreHistory {

    @Id @UuidGenerator(style = UuidGenerator.Style.TIME)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "user_id", nullable = false)  private UUID userId;
    @Column(name = "scale_id", nullable = false) private UUID scaleId;
    @Column(name = "test_id", nullable = false)  private UUID testId;
    @Column(name = "result_id", nullable = false) private UUID resultId;

    @Column(name = "z_score", precision = 6, scale = 3) private BigDecimal zScore;
    @Column(name = "t_score", precision = 6, scale = 2) private BigDecimal tScore;
    @Column(name = "sten_score", precision = 4, scale = 2) private BigDecimal stenScore;
    @Column(precision = 5, scale = 2) private BigDecimal percentile;

    /** Exact norm version used at score time — frozen, never re-scored (D5). */
    @Column(name = "norm_table_version_id") private UUID normTableVersionId;

    @Column(name = "scored_at", nullable = false) private LocalDateTime scoredAt;

    @Column(name = "created_at", nullable = false) @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
