package com.edge.pulse.data.models.psychometric;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_item_exposure")
@IdClass(UserItemExposureId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserItemExposure {

    @Id @Column(name = "user_id", nullable = false)     private UUID userId;
    @Id @Column(name = "question_id", nullable = false) private UUID questionId;

    @Column(name = "test_id", nullable = false) private UUID testId;

    @Column(name = "first_seen", nullable = false) @Builder.Default
    private LocalDateTime firstSeen = LocalDateTime.now();

    @Column(name = "answered_at") private LocalDateTime answeredAt;
}
