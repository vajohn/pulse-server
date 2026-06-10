package com.edge.pulse.data.models.spark;

import com.edge.pulse.data.enums.AwardPeriodStatus;
import com.edge.pulse.data.models.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "award_periods")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AwardPeriod {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "nomination_start", nullable = false)
    private LocalDateTime nominationStart;

    @Column(name = "nomination_end", nullable = false)
    private LocalDateTime nominationEnd;

    @Column(name = "voting_start", nullable = false)
    private LocalDateTime votingStart;

    @Column(name = "voting_end", nullable = false)
    private LocalDateTime votingEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AwardPeriodStatus status = AwardPeriodStatus.UPCOMING;

    @Column(name = "eligible_entities")
    private String eligibleEntities;

    @Column(name = "award_amount")
    private BigDecimal awardAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
