package com.edge.pulse.data.models.psychometric;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "norm_entry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"normTable", "scale"})
public class NormEntry {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "norm_table_id", nullable = false)
    private NormTableVersion normTable;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scale_id", nullable = false)
    private PsychometricScale scale;

    @Column(name = "raw_score_min", nullable = false)
    private BigDecimal rawScoreMin;

    @Column(name = "raw_score_max", nullable = false)
    private BigDecimal rawScoreMax;

    /** 0.00–99.99 */
    @Column(precision = 5, scale = 2)
    private BigDecimal percentile;

    /** 1–10 */
    @Column(name = "sten_score")
    private Integer stenScore;

    @Column(name = "z_score", precision = 6, scale = 3)
    private BigDecimal zScore;
}
