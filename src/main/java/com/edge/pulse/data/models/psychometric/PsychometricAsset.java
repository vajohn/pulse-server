package com.edge.pulse.data.models.psychometric;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "psychometric_asset")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PsychometricAsset {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String sha256;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private int byteSize;

    @Column(length = 8)
    private String locale;

    @Column
    private byte[] data;

    @Column(name = "storage_uri", length = 1024)
    private String storageUri;

    @Column(name = "original_name")
    private String originalName;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
