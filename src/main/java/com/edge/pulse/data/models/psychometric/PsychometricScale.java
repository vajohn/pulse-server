package com.edge.pulse.data.models.psychometric;

import com.edge.pulse.data.enums.CompositeBasis;
import com.edge.pulse.data.enums.CompositeMethod;
import com.edge.pulse.data.enums.ResultMode;
import com.edge.pulse.data.enums.ScoreMethod;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "psychometric_scale")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"test", "parentScale"})
public class PsychometricScale {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_id", nullable = false)
    private PsychometricTest test;

    /** NULL = root scale; non-null = child/subscale of the referenced scale. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_scale_id")
    private PsychometricScale parentScale;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "score_method", nullable = false)
    @Builder.Default
    private ScoreMethod scoreMethod = ScoreMethod.SUM;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    /** NULL = leaf scale scored directly from items; non-null = composite aggregated from children. */
    @Enumerated(EnumType.STRING)
    @Column(name = "composite_method")
    private CompositeMethod compositeMethod;

    /** Which score type children contribute when this scale is a composite. NULL for leaf scales. */
    @Enumerated(EnumType.STRING)
    @Column(name = "composite_basis")
    private CompositeBasis compositeBasis;

    /** Decimal places for composite rollup rounding. NULL = default 1 dp. */
    @Column(name = "composite_rounding_scale")
    private Integer compositeRoundingScale;

    /** How this scale's result is released (D3). IMMEDIATE = score when its items complete in a
     *  session; CONSOLIDATED = accrue across micro-sessions and score only when full set answered. */
    @Enumerated(EnumType.STRING)
    @Column(name = "result_mode", nullable = false)
    @Builder.Default
    private ResultMode resultMode = ResultMode.IMMEDIATE;

    /** CWB / validity scales (§6.3) are interpreter-only — NEVER aggregated or trended (D3).
     *  Sourced from the import scoring-sheet `restricted` flag. */
    @Column(name = "restricted", nullable = false)
    @Builder.Default
    private boolean restricted = false;
}
