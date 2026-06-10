package com.edge.pulse.data.models;

import com.edge.pulse.data.enums.QuestionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "question")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"form", "candidateAnswers"})
public class Question {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "form_id", nullable = false)
    private Form form;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false)
    private QuestionType questionType;

    private LocalDateTime effectiveDate;
    private LocalDateTime expirationDate;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "subject_labels", columnDefinition = "jsonb")
    private List<String> subjectLabels;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CandidateAnswer> candidateAnswers;

    /** Minimum value for SCALE-type psychometric questions (e.g. 1). NULL for non-SCALE questions. */
    @Column(name = "scale_min")
    private Integer scaleMin;

    /** Maximum value for SCALE-type psychometric questions (e.g. 5). NULL for non-SCALE questions. */
    @Column(name = "scale_max")
    private Integer scaleMax;

    /** Left-end label for the scale (e.g. "Strongly Disagree"). NULL for non-SCALE questions. */
    @Column(name = "min_label")
    private String minLabel;

    /** Right-end label for the scale (e.g. "Strongly Agree"). NULL for non-SCALE questions. */
    @Column(name = "max_label")
    private String maxLabel;

    /**
     * Statement pairs for FORCED_CHOICE questions, stored as JSONB.
     *
     * <p>Example value:
     * <pre>
     * [{"a": "I enjoy leading teams",  "scaleA": "<uuid>",
     *   "b": "I prefer working alone", "scaleB": "<uuid>",
     *   "aAr": "أنا أستمتع بقيادة الفرق", "bAr": "أفضل العمل بمفردي"}]
     * </pre>
     * aAr/bAr are stored inline in the same pair map — no separate forced_choice_pairs_ar
     * column — to prevent positional drift when pairs are reordered.
     * NULL for all other question types.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "forced_choice_pairs", columnDefinition = "jsonb")
    private List<java.util.Map<String, Object>> forcedChoicePairs;

    // ── Arabic translation fields (V2 migration) ──────────────────────────────

    /** Arabic translation of {@link #body}. NULL when not yet translated. */
    @Column(name = "body_ar", columnDefinition = "TEXT")
    private String bodyAr;

    /** Arabic translation of {@link #minLabel}. NULL when not yet translated. */
    @Column(name = "min_label_ar")
    private String minLabelAr;

    /** Arabic translation of {@link #maxLabel}. NULL when not yet translated. */
    @Column(name = "max_label_ar")
    private String maxLabelAr;

    /**
     * Arabic translations of {@link #subjectLabels}, stored as a parallel positional JSONB array.
     * Index N in this list is the Arabic translation of index N in subjectLabels.
     * Safe as a positional array because the admin UI provides add/remove only — no reorder.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "subject_labels_ar", columnDefinition = "jsonb")
    private List<String> subjectLabelsAr;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isActiveOn(LocalDateTime date) {
        if (effectiveDate != null && date.isBefore(effectiveDate)) {
            return false;
        }
        if (expirationDate != null && date.isAfter(expirationDate)) {
            return false;
        }
        return true;
    }
}
