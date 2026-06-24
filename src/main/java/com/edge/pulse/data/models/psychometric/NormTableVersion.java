package com.edge.pulse.data.models.psychometric;

import com.edge.pulse.data.enums.NormStatus;
import com.edge.pulse.data.enums.NormStrategyType;
import com.edge.pulse.data.models.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "norm_table_version")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"test", "publishedBy"})
public class NormTableVersion {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_id", nullable = false)
    private PsychometricTest test;

    @Column(nullable = false)
    private int version;

    /** e.g. "UAE Military Officers 2026" */
    @Column(nullable = false)
    private String label;

    private Integer sampleSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private NormStatus status = NormStatus.PROVISIONAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "norm_strategy", nullable = false)
    @Builder.Default
    private NormStrategyType normStrategy = NormStrategyType.PARAMETRIC;

    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveUntil;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by")
    private User publishedBy;

    private LocalDateTime publishedAt;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
