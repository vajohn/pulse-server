package com.edge.pulse.mappers;

import com.edge.pulse.data.dto.spark.*;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.spark.*;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class SparkMapper {

    public AwardPeriodDto toDto(AwardPeriod entity) {
        return new AwardPeriodDto(
                entity.getId(),
                entity.getName(),
                entity.getNominationStart(),
                entity.getNominationEnd(),
                entity.getVotingStart(),
                entity.getVotingEnd(),
                entity.getStatus(),
                entity.getEligibleEntities(),
                entity.getAwardAmount(),
                entity.getCreatedAt()
        );
    }

    public SparkCategoryDto toDto(SparkCategory entity) {
        return new SparkCategoryDto(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getIcon(),
                entity.getDisplayOrder(),
                entity.isActive()
        );
    }

    public NomineeInfoDto toNomineeInfo(User user) {
        return new NomineeInfoDto(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getTitle() != null ? user.getTitle().getName() : null,
                user.getOrgUnit() != null ? user.getOrgUnit().getOrgUnitName() : null
        );
    }

    public AttachmentDto toDto(NominationAttachment entity) {
        return new AttachmentDto(
                entity.getId(),
                entity.getFileName(),
                entity.getFileType(),
                entity.getFileSize(),
                entity.getStorageUrl(),
                entity.getUploadedAt()
        );
    }

    public NominationDto toDto(Nomination entity) {
        return toDtoWithAnonymity(entity, false);
    }

    public NominationDto toDtoAnonymized(Nomination entity) {
        return toDtoWithAnonymity(entity, true);
    }

    private NominationDto toDtoWithAnonymity(Nomination entity, boolean hideNominator) {
        List<AttachmentDto> attachments = entity.getAttachments() != null
                ? entity.getAttachments().stream().map(this::toDto).toList()
                : Collections.emptyList();
        return new NominationDto(
                entity.getId(),
                SparkReference.format(entity.getId()),
                entity.getAwardPeriod().getId(),
                entity.getAwardPeriod().getName(),
                toDto(entity.getCategory()),
                toNomineeInfo(entity.getNominee()),
                hideNominator ? null : entity.getNominator().getId(),
                hideNominator ? null : entity.getNominator().getDisplayName(),
                entity.getJustification(),
                entity.getStatus(),
                entity.getSubmittedAt(),
                attachments
        );
    }

    public LeaderVoteDto toDto(LeaderVote entity) {
        return new LeaderVoteDto(
                entity.getId(),
                entity.getAwardPeriod().getId(),
                toDto(entity.getCategory()),
                toNomineeInfo(entity.getNominee().getNominee()),
                entity.getEndorsementComment(),
                entity.getVotedAt()
        );
    }

    public SparkWinnerDto toDto(SparkWinner entity, long congratulationCount) {
        return new SparkWinnerDto(
                entity.getId(),
                entity.getAwardPeriod().getId(),
                entity.getAwardPeriod().getName(),
                toDto(entity.getCategory()),
                toNomineeInfo(entity.getWinner()),
                entity.getVoteCount(),
                entity.getAnnouncedAt(),
                entity.getAwardPoints(),
                congratulationCount
        );
    }

    public SparkCongratulationDto toDto(SparkCongratulation entity) {
        return new SparkCongratulationDto(
                entity.getId(),
                entity.getUser().getId(),
                entity.getUser().getDisplayName(),
                entity.getReaction(),
                entity.getMessage(),
                entity.getCreatedAt()
        );
    }
}
