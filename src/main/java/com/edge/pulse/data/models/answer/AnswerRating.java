package com.edge.pulse.data.models.answer;

import com.edge.pulse.data.models.AnswerSubmission;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import java.util.UUID;

@Entity
@Table(name = "answer_rating")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"submission"})
public class AnswerRating {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private AnswerSubmission submission;

    @Column(name = "subject_label", nullable = false)
    private String subjectLabel;

    @Column(nullable = false)
    private int stars;

    @Column(name = "max_stars", nullable = false)
    private int maxStars;
}
