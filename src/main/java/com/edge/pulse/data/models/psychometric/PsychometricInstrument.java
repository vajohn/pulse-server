package com.edge.pulse.data.models.psychometric;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "psychometric_instrument")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PsychometricInstrument {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    /** InstrumentNormalizer.canonical(displayName) — UNIQUE in DB; the dedup guard. */
    @Column(name = "canonical_name", nullable = false, unique = true)
    private String canonicalName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
