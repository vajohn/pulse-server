package com.edge.pulse.data.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "answer_scale")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"submission"})
public class AnswerScale {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @EqualsAndHashCode.Include
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private AnswerSubmission submission;

    @Column(nullable = false)
    private int value;

    @Column(name = "min_value", nullable = false)
    @Builder.Default
    private int minValue = 1;

    @Column(name = "max_value", nullable = false)
    @Builder.Default
    private int maxValue = 5;
}
