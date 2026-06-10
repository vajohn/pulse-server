package com.edge.pulse.data.models.psychometric;

import com.edge.pulse.data.models.CandidateAnswer;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/**
 * Junction table: one row per (scoring_key_item, correct_candidate_answer).
 *
 * <p>Used exclusively for {@code CHOICE_MULTIPLE} questions where a correct
 * response requires selecting an exact set of options.
 *
 * <p>Mapped to the {@code scoring_key_correct_answers} table created in
 * Flyway V18.
 */
@Entity
@Table(name = "scoring_key_correct_answers",
       uniqueConstraints = @UniqueConstraint(
               columnNames = {"scoring_key_item_id", "candidate_answer_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"scoringKeyItem", "candidateAnswer"})
public class ScoringKeyCorrectAnswer {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scoring_key_item_id", nullable = false)
    private ScoringKeyItem scoringKeyItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_answer_id", nullable = false)
    private CandidateAnswer candidateAnswer;
}
