package com.edge.pulse.services.spark;

import com.edge.pulse.data.dto.PagedResponse;
import com.edge.pulse.data.dto.spark.*;
import com.edge.pulse.data.enums.AwardPeriodStatus;
import org.springframework.security.core.Authentication;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.spark.AwardPeriod;
import com.edge.pulse.data.models.spark.SparkCategory;
import com.edge.pulse.mappers.SparkMapper;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.repositories.spark.AwardPeriodRepository;
import com.edge.pulse.repositories.spark.NominationRepository;
import com.edge.pulse.repositories.spark.SparkCategoryRepository;
import com.edge.pulse.repositories.spark.SparkWinnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SparkService {

    private final AwardPeriodRepository awardPeriodRepository;
    private final SparkCategoryRepository categoryRepository;
    private final NominationRepository nominationRepository;
    private final SparkWinnerRepository winnerRepository;
    private final UserRepository userRepository;
    private final SparkMapper sparkMapper;

    public SparkHomeDto getHome(UUID userId, boolean isLeader) {
        AwardPeriod current = findActivePeriod().orElse(null);
        AwardPeriodDto periodDto = current != null ? sparkMapper.toDto(current) : null;

        List<NominationDto> myNominations = current != null
                ? nominationRepository.findByNominatorIdAndAwardPeriodId(userId, current.getId())
                        .stream().map(sparkMapper::toDto).toList()
                : Collections.emptyList();

        List<SparkWinnerDto> recentWinners = current != null
                ? winnerRepository.findByAwardPeriodIdAndAnnouncedAtIsNotNull(current.getId())
                        .stream().map(w -> sparkMapper.toDto(w, 0)).toList()
                : Collections.emptyList();

        int pendingVotes = 0;
        if (isLeader && current != null && current.getStatus() == AwardPeriodStatus.VOTING_OPEN) {
            pendingVotes = (int) categoryRepository.findByActiveTrueOrderByDisplayOrderAsc()
                    .stream().count(); // simplified: all active categories
        }

        int badge = calculateBadge(current, userId, isLeader);

        return new SparkHomeDto(periodDto, badge, myNominations, recentWinners, pendingVotes);
    }

    private int calculateBadge(AwardPeriod period, UUID userId, boolean isLeader) {
        if (period == null) return 0;
        return switch (period.getStatus()) {
            case NOMINATION_OPEN -> 1;
            case VOTING_OPEN -> isLeader ? 1 : 0;
            case ANNOUNCED -> 1;
            default -> 0;
        };
    }

    public Optional<AwardPeriod> findActivePeriod() {
        List<AwardPeriodStatus> activeStatuses = List.of(
                AwardPeriodStatus.NOMINATION_OPEN,
                AwardPeriodStatus.NOMINATION_CLOSED,
                AwardPeriodStatus.VOTING_OPEN,
                AwardPeriodStatus.VOTING_CLOSED,
                AwardPeriodStatus.FINALIZED,
                AwardPeriodStatus.ANNOUNCED
        );
        return awardPeriodRepository.findByStatusInOrderByNominationStartDesc(activeStatuses)
                .stream().findFirst();
    }

    public AwardPeriod getPeriodOrThrow(UUID id) {
        return awardPeriodRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Award period not found: " + id));
    }

    public List<AwardPeriodDto> getAllPeriods() {
        return awardPeriodRepository.findAllByOrderByNominationStartDesc()
                .stream().map(sparkMapper::toDto).toList();
    }

    public PagedResponse<AwardPeriodDto> getPagedPeriods(
            int page, int size, AwardPeriodStatus status) {
        int cappedSize = Math.min(size, 50);
        PageRequest pageable = PageRequest.of(page, cappedSize);

        Page<AwardPeriod> result = (status != null)
                ? awardPeriodRepository.findByStatusOrderByNominationStartDesc(status, pageable)
                : awardPeriodRepository.findAllByOrderByNominationStartDesc(pageable);

        return new PagedResponse<>(
                result.getContent().stream().map(sparkMapper::toDto).toList(),
                result.hasNext()
        );
    }

    public List<SparkCategoryDto> getActiveCategories() {
        return categoryRepository.findByActiveTrueOrderByDisplayOrderAsc()
                .stream().map(sparkMapper::toDto).toList();
    }

    public SparkCategory getCategoryOrThrow(String id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));
    }

    public boolean isLeader(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("SPARK_VOTE"));
    }

    public Optional<AwardPeriodDto> getCurrentPeriod() {
        return findActivePeriod().map(sparkMapper::toDto);
    }

    public List<NomineeInfoDto> searchEmployees(String query) {
        return userRepository.findByDisplayNameContainingIgnoreCaseOrEmailContainingIgnoreCase(query, query)
                .stream()
                .filter(User::isActive)
                .map(sparkMapper::toNomineeInfo)
                .toList();
    }
}
