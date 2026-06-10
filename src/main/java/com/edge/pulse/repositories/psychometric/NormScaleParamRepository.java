package com.edge.pulse.repositories.psychometric;

import com.edge.pulse.data.models.psychometric.NormScaleParam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NormScaleParamRepository extends JpaRepository<NormScaleParam, UUID> {

    Optional<NormScaleParam> findByNormTable_IdAndScale_Id(UUID normTableVersionId, UUID scaleId);
}
