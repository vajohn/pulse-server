package com.edge.pulse.services.spark;

import com.edge.pulse.data.dto.spark.AwardPeriodDto;
import com.edge.pulse.data.dto.spark.CreateAwardPeriodRequest;
import com.edge.pulse.data.dto.spark.NominationDto;
import com.edge.pulse.data.enums.AwardPeriodStatus;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.spark.AwardPeriod;
import com.edge.pulse.mappers.SparkMapper;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.repositories.spark.AwardPeriodRepository;
import com.edge.pulse.repositories.spark.NominationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SparkAdminService {

    private final AwardPeriodRepository awardPeriodRepository;
    private final NominationRepository nominationRepository;
    private final UserRepository userRepository;
    private final SparkMapper sparkMapper;

    public AwardPeriodDto createPeriod(UUID createdById, CreateAwardPeriodRequest request) {
        User createdBy = userRepository.findById(createdById)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + createdById));

        AwardPeriod period = AwardPeriod.builder()
                .name(request.name())
                .nominationStart(request.nominationStart())
                .nominationEnd(request.nominationEnd())
                .votingStart(request.votingStart())
                .votingEnd(request.votingEnd())
                .status(AwardPeriodStatus.UPCOMING)
                .eligibleEntities(request.eligibleEntities())
                .awardAmount(request.awardAmount())
                .createdBy(createdBy)
                .build();

        return sparkMapper.toDto(awardPeriodRepository.save(period));
    }

    public AwardPeriodDto updatePeriod(UUID periodId, CreateAwardPeriodRequest request) {
        AwardPeriod period = awardPeriodRepository.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("Award period not found: " + periodId));

        if (period.getStatus() != AwardPeriodStatus.UPCOMING) {
            throw new IllegalStateException("Cannot edit a period that has already started");
        }

        period.setName(request.name());
        period.setNominationStart(request.nominationStart());
        period.setNominationEnd(request.nominationEnd());
        period.setVotingStart(request.votingStart());
        period.setVotingEnd(request.votingEnd());
        if (request.eligibleEntities() != null) period.setEligibleEntities(request.eligibleEntities());
        if (request.awardAmount() != null) period.setAwardAmount(request.awardAmount());

        return sparkMapper.toDto(awardPeriodRepository.save(period));
    }

    public AwardPeriodDto advancePeriodStatus(UUID periodId) {
        AwardPeriod period = awardPeriodRepository.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("Award period not found: " + periodId));

        AwardPeriodStatus next = switch (period.getStatus()) {
            case UPCOMING -> AwardPeriodStatus.NOMINATION_OPEN;
            case NOMINATION_OPEN -> AwardPeriodStatus.NOMINATION_CLOSED;
            case NOMINATION_CLOSED -> AwardPeriodStatus.VOTING_OPEN;
            case VOTING_OPEN -> AwardPeriodStatus.VOTING_CLOSED;
            case VOTING_CLOSED -> AwardPeriodStatus.FINALIZED;
            case ANNOUNCED -> AwardPeriodStatus.EXPIRED;
            default -> throw new IllegalStateException("Cannot advance status from: " + period.getStatus());
        };

        period.setStatus(next);
        return sparkMapper.toDto(awardPeriodRepository.save(period));
    }

    private static final Set<AwardPeriodStatus> CANCELLABLE_STATUSES = Set.of(
            AwardPeriodStatus.UPCOMING,
            AwardPeriodStatus.NOMINATION_OPEN,
            AwardPeriodStatus.NOMINATION_CLOSED,
            AwardPeriodStatus.VOTING_OPEN,
            AwardPeriodStatus.VOTING_CLOSED
    );

    public AwardPeriodDto cancelPeriod(UUID periodId) {
        AwardPeriod period = awardPeriodRepository.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("Award period not found: " + periodId));

        if (!CANCELLABLE_STATUSES.contains(period.getStatus())) {
            throw new IllegalStateException("Cannot cancel a period in status: " + period.getStatus());
        }

        period.setStatus(AwardPeriodStatus.CANCELLED);
        return sparkMapper.toDto(awardPeriodRepository.save(period));
    }

    @Transactional(readOnly = true)
    public List<NominationDto> getAllNominations(UUID periodId) {
        return nominationRepository.findByAwardPeriodId(periodId)
                .stream().map(sparkMapper::toDto).toList();
    }
}
