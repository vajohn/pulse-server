package com.edge.pulse.data.models.psychometric;

import com.edge.pulse.data.enums.ResultAudience;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "result_visibility_policy")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"test"})
public class ResultVisibilityPolicy {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_id", nullable = false)
    private PsychometricTest test;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResultAudience audience;

    @Column(name = "show_raw_score", nullable = false)
    @Builder.Default
    private boolean showRawScore = false;

    @Column(name = "show_sten_profile", nullable = false)
    @Builder.Default
    private boolean showStenProfile = false;

    @Column(name = "show_percentile", nullable = false)
    @Builder.Default
    private boolean showPercentile = false;

    @Column(name = "show_competency_map", nullable = false)
    @Builder.Default
    private boolean showCompetencyMap = false;

    /** Default: show pass/fail only — no scores. Overridable per test. */
    @Column(name = "show_pass_fail_only", nullable = false)
    @Builder.Default
    private boolean showPassFailOnly = true;

    @Column(name = "show_scale_breakdown", nullable = false)
    @Builder.Default
    private boolean showScaleBreakdown = false;
}
