package com.edge.pulse.data.models.psychometric;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "scale_score")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"result", "scale"})
public class ScaleScore {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "result_id", nullable = false)
    private TestResult result;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scale_id", nullable = false)
    private PsychometricScale scale;

    @Column(name = "raw_score", nullable = false)
    private BigDecimal rawScore;

    /** NULL until a norm table is available and norming has been run. */
    @Column(name = "z_score", precision = 6, scale = 3)
    private BigDecimal zScore;

    /** 1–10 (decimal); NULL until normed. */
    @Column(name = "sten_score", precision = 4, scale = 2)
    private BigDecimal stenScore;

    /** T-score; NULL until normed. */
    @Column(name = "t_score", precision = 6, scale = 2)
    private BigDecimal tScore;

    /** 0.00–99.99; NULL until normed. */
    @Column(precision = 5, scale = 2)
    private BigDecimal percentile;

    @Column(name = "items_answered", nullable = false)
    private int itemsAnswered;

    @Column(name = "items_total", nullable = false)
    private int itemsTotal;
}
