package com.edge.pulse.data.models.psychometric;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

/** Phase 4 — one row per (test result, competency) produced by the scoring engine. */
@Entity
@Table(name = "competency_score")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"result", "competency"})
public class CompetencyScore {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "result_id", nullable = false)
    private TestResult result;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "competency_id", nullable = false)
    private Competency competency;

    /** Normalized 0.0–10.0 competency score (weighted sten average). */
    @Column(nullable = false, precision = 5, scale = 3)
    private BigDecimal score;
}
