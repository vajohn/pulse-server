package com.edge.pulse.data.models.psychometric;

import com.edge.pulse.data.enums.ScoreDirection;
import com.edge.pulse.data.models.CandidateAnswer;
import com.edge.pulse.data.models.Question;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "scoring_key_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"scoringKey", "scale", "question", "correctAnswer"})
public class ScoringKeyItem {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scoring_key_id", nullable = false)
    private ScoringKeyVersion scoringKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scale_id", nullable = false)
    private PsychometricScale scale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ScoreDirection direction = ScoreDirection.FORWARD;

    @Column(nullable = false, precision = 6, scale = 3)
    @Builder.Default
    private BigDecimal weight = BigDecimal.ONE;

    /**
     * The keyed correct answer for cognitive (CHOICE_SINGLE / FORCED_CHOICE) items.
     * NULL for SCALE and ADJECTIVE_CHECKLIST items.
     * For CHOICE_MULTIPLE, the correct set is stored in {@code scoring_key_correct_answers}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "correct_answer_id")
    private CandidateAnswer correctAnswer;

    /**
     * Partial-credit mode for CHOICE_MULTIPLE items.
     *
     * <ul>
     *   <li>{@code false} (default) — full weight only when the candidate selects
     *       exactly the keyed correct set; otherwise 0.</li>
     *   <li>{@code true} — +1 per correct option selected, −0.25 per wrong option
     *       selected, clamped to ≥ 0.</li>
     * </ul>
     */
    @Column(name = "partial_credit", nullable = false)
    @Builder.Default
    private boolean partialCredit = false;
}
