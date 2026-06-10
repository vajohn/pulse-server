package com.edge.pulse.repositories.spark;

import com.edge.pulse.data.enums.AwardPeriodStatus;
import com.edge.pulse.data.models.spark.AwardPeriod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AwardPeriodRepository extends JpaRepository<AwardPeriod, UUID> {

    Optional<AwardPeriod> findFirstByStatusOrderByNominationStartDesc(AwardPeriodStatus status);

    List<AwardPeriod> findAllByOrderByNominationStartDesc();

    Page<AwardPeriod> findAllByOrderByNominationStartDesc(Pageable pageable);

    List<AwardPeriod> findByStatusInOrderByNominationStartDesc(List<AwardPeriodStatus> statuses);

    Page<AwardPeriod> findByStatusOrderByNominationStartDesc(AwardPeriodStatus status, Pageable pageable);
}
