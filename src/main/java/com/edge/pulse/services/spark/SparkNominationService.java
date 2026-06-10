package com.edge.pulse.services.spark;

import com.edge.pulse.data.dto.spark.AddAttachmentRequest;
import com.edge.pulse.data.dto.spark.NominationDto;
import com.edge.pulse.data.dto.spark.SubmitNominationRequest;
import com.edge.pulse.data.enums.AwardPeriodStatus;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.spark.AwardPeriod;
import com.edge.pulse.data.models.spark.Nomination;
import com.edge.pulse.data.models.spark.NominationAttachment;
import com.edge.pulse.data.models.spark.SparkCategory;
import com.edge.pulse.mappers.SparkMapper;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.repositories.spark.NominationAttachmentRepository;
import com.edge.pulse.repositories.spark.NominationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SparkNominationService {

    private final NominationRepository nominationRepository;
    private final NominationAttachmentRepository attachmentRepository;
    private final UserRepository userRepository;
    private final SparkService sparkService;
    private final SparkMapper sparkMapper;

    public NominationDto submit(UUID nominatorId, SubmitNominationRequest request) {
        AwardPeriod period = sparkService.getPeriodOrThrow(request.awardPeriodId());
        if (period.getStatus() != AwardPeriodStatus.NOMINATION_OPEN) {
            throw new IllegalStateException("Nominations are not open for this period");
        }

        User nominator = getUserOrThrow(nominatorId);
        User nominee = getUserOrThrow(request.nomineeId());

        if (nominatorId.equals(request.nomineeId())) {
            throw new IllegalArgumentException("You cannot nominate yourself");
        }

        if (nominationRepository.existsByAwardPeriodIdAndCategoryIdAndNominatorId(
                request.awardPeriodId(), request.categoryId(), nominatorId)) {
            throw new IllegalStateException("You already have a nomination in this category for this period");
        }

        SparkCategory category = sparkService.getCategoryOrThrow(request.categoryId());

        Nomination nomination = Nomination.builder()
                .awardPeriod(period)
                .category(category)
                .nominator(nominator)
                .nominee(nominee)
                .justification(request.justification())
                .build();

        return sparkMapper.toDto(nominationRepository.save(nomination));
    }

    @Transactional(readOnly = true)
    public List<NominationDto> getMyNominations(UUID userId) {
        return nominationRepository.findByNominatorId(userId)
                .stream().map(sparkMapper::toDto).toList();
    }

    /**
     * Returns all nominations for a period+category, with nominator identity stripped.
     * Used by entity leaders (SPARK_VOTE) to see who they can vote for.
     */
    @Transactional(readOnly = true)
    public List<NominationDto> getVotingNominations(UUID periodId, String categoryId) {
        return nominationRepository.findByAwardPeriodIdAndCategoryId(periodId, categoryId)
                .stream().map(sparkMapper::toDtoAnonymized).toList();
    }

    @Transactional(readOnly = true)
    public NominationDto getNomination(UUID nominationId) {
        return sparkMapper.toDto(getNominationOrThrow(nominationId));
    }

    public NominationDto addAttachment(UUID nominationId, UUID uploadedById, AddAttachmentRequest request) {
        Nomination nomination = getNominationOrThrow(nominationId);
        User uploadedBy = getUserOrThrow(uploadedById);

        // Stub: storageUrl is null — no actual upload
        NominationAttachment attachment = NominationAttachment.builder()
                .nomination(nomination)
                .fileName(request.fileName())
                .fileType(request.fileType())
                .fileSize(request.fileSize())
                .storageUrl(null)
                .uploadedBy(uploadedBy)
                .build();

        attachmentRepository.save(attachment);
        return sparkMapper.toDto(nominationRepository.findById(nominationId).orElseThrow());
    }

    public void deleteAttachment(UUID nominationId, UUID attachmentId, UUID requesterId) {
        Nomination nomination = getNominationOrThrow(nominationId);
        if (!nomination.getNominator().getId().equals(requesterId)) {
            throw new IllegalArgumentException("You can only remove attachments from your own nominations");
        }
        attachmentRepository.deleteById(attachmentId);
    }

    private Nomination getNominationOrThrow(UUID id) {
        return nominationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nomination not found: " + id));
    }

    private User getUserOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }
}
