package com.edge.pulse.data.models.psychometric;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Parametric-normal norm for a single scale within a {@link NormTableVersion}.
 * Stores the {@code mean} and {@code sd} the psychometrics team supplies each refresh;
 * STEN/percentile are computed analytically by {@code NormStandardizer} at scoring time.
 */
@Entity
@Table(name = "norm_scale_param")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"normTable", "scale"})
public class NormScaleParam {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "norm_table_version_id", nullable = false)
    private NormTableVersion normTable;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scale_id", nullable = false)
    private PsychometricScale scale;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal mean;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal sd;

    @Column(name = "sample_size")
    private Integer sampleSize;

    /** Multiplier for T-score formula: T = tFactor * z + tOffset (default 10 = personality scale). */
    @Column(name = "t_factor", nullable = false, precision = 6, scale = 3)
    @Builder.Default
    private BigDecimal tFactor = BigDecimal.TEN;

    /** Offset for T-score formula (default 50 = personality scale). */
    @Column(name = "t_offset", nullable = false, precision = 6, scale = 3)
    @Builder.Default
    private BigDecimal tOffset = new BigDecimal("50.000");

    /** Lower clip bound for T-score (default 10). */
    @Column(name = "t_clip_lo", nullable = false, precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal tClipLo = new BigDecimal("10.00");

    /** Upper clip bound for T-score (default 120). */
    @Column(name = "t_clip_hi", nullable = false, precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal tClipHi = new BigDecimal("120.00");
}
