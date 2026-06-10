package com.edge.pulse.repositories.spark;

import com.edge.pulse.data.models.spark.NominationAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NominationAttachmentRepository extends JpaRepository<NominationAttachment, UUID> {

    List<NominationAttachment> findByNominationId(UUID nominationId);

    void deleteByNominationId(UUID nominationId);
}
