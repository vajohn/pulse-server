package com.edge.pulse.data.models.psychometric;

import com.edge.pulse.data.enums.ScoreDirection;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

/** Phase 4 — maps psychometric scales to UAE military competencies with a weight. */
@Entity
@Table(name = "competency_scale_weight")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(CompetencyScaleWeight.CompetencyScaleWeightId.class)
public class CompetencyScaleWeight {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "competency_id", nullable = false)
    private Competency competency;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scale_id", nullable = false)
    private PsychometricScale scale;

    @Column(nullable = false, precision = 5, scale = 3)
    @Builder.Default
    private BigDecimal weight = BigDecimal.ONE;

    /** FORWARD: sten contributes positively to the competency score.
     *  REVERSE: sten is inverted (11 − sten) before weighting — used for dark-side or negatively-keyed scales. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ScoreDirection direction = ScoreDirection.FORWARD;

    @EqualsAndHashCode
    public static class CompetencyScaleWeightId implements Serializable {
        private UUID competency;
        private UUID scale;
    }
}
