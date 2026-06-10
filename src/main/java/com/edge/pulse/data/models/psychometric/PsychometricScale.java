package com.edge.pulse.data.models.psychometric;

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
}
