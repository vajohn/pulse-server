package com.edge.pulse.repositories.psychometric;

import com.edge.pulse.data.models.psychometric.PsychometricAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PsychometricAssetRepository extends JpaRepository<PsychometricAsset, UUID> {
    Optional<PsychometricAsset> findBySha256(String sha256);
}
