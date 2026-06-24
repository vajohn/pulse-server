package com.edge.pulse.data.models.psychometric;

import com.edge.pulse.data.enums.ScaleProgressState;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "scale_progress")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ScaleProgress {

    @Id @UuidGenerator(style = UuidGenerator.Style.TIME)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "user_id", nullable = false)  private UUID userId;
    @Column(name = "scale_id", nullable = false) private UUID scaleId;
    @Column(name = "test_id", nullable = false)  private UUID testId;
    @Column(name = "window_id", nullable = false) private UUID windowId;

    /** Norm version frozen at window open (D4). NULL only if no VALIDATED norm existed then. */
    @Column(name = "norm_table_version_id") private UUID normTableVersionId;

    @Column(name = "items_required", nullable = false)  private int itemsRequired;
    @Column(name = "items_collected", nullable = false) @Builder.Default
    private int itemsCollected = 0;

    @Enumerated(EnumType.STRING) @Column(nullable = false) @Builder.Default
    private ScaleProgressState state = ScaleProgressState.COLLECTING;

    @Column(name = "opened_at", nullable = false) @Builder.Default
    private LocalDateTime openedAt = LocalDateTime.now();

    @Column(name = "consolidated_at") private LocalDateTime consolidatedAt;
}
