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
}
