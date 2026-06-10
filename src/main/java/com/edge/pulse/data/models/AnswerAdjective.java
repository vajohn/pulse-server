package com.edge.pulse.data.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

/**
 * Stores the adjective labels selected by a candidate for an
 * {@code ADJECTIVE_CHECKLIST} question.
 *
 * <p>Mapped to the {@code answer_adjective} table created in Flyway V18.
 */
@Entity
@Table(name = "answer_adjective")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"submission"})
public class AnswerAdjective {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @EqualsAndHashCode.Include
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private AnswerSubmission submission;

    /** Selected adjective labels, stored as a JSONB array. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> selected = List.of();
}
