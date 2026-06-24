package com.edge.pulse.data.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import java.util.UUID;

@Entity
@Table(name = "candidate_answer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"question"})
public class CandidateAnswer {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(nullable = false)
    private String label;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** Arabic translation of {@link #label}. NULL when not yet translated. */
    @Column(name = "label_ar", length = 512)
    private String labelAr;

    /**
     * Display-only flag indicating whether this option is the keyed correct answer for a
     * cognitive item. Intended for admin review UI only.
     *
     * <p><strong>Scoring uses {@link com.edge.pulse.data.models.psychometric.ScoringKeyItem#getCorrectAnswer()}
     * (the FK on the scoring key), NOT this field.</strong> Keeping this flag in sync with the
     * scoring key is the admin's responsibility. Discrepancies are non-critical because the
     * scoring engine never reads this column.
     *
     * <p>NULL = not a cognitive item; TRUE = this option is correct for display purposes.
     * NEVER serialized to JSON responses — see {@link JsonIgnore}.
     */
    @JsonIgnore
    @Column(name = "is_correct")
    private Boolean isCorrect;

    /**
     * For OPTION_TAGGED_TALLY (VIP) scoring: the scale this option's tally is credited to.
     * NULL for all non-VIP answer options.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_scale_id")
    private com.edge.pulse.data.models.psychometric.PsychometricScale tagScale;
}
