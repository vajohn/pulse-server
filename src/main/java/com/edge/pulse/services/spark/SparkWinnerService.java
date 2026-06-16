package com.edge.pulse.services.spark;

import com.edge.pulse.data.dto.spark.CongratulateRequest;
import com.edge.pulse.data.dto.spark.FinalizeWinnerRequest;
import com.edge.pulse.data.dto.spark.SparkCongratulationDto;
import com.edge.pulse.data.dto.spark.SparkWinnerDto;
import com.edge.pulse.data.enums.AwardPeriodStatus;
import com.edge.pulse.data.enums.NominationStatus;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.spark.AwardPeriod;
import com.edge.pulse.data.models.spark.SparkCongratulation;
import com.edge.pulse.data.models.spark.SparkWinner;
import com.edge.pulse.mappers.SparkMapper;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.repositories.spark.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SparkWinnerService {

    private final SparkWinnerRepository winnerRepository;
    private final SparkCongratulationRepository congratulationRepository;
    private final NominationRepository nominationRepository;
    private final LeaderVoteRepository voteRepository;
    private final UserRepository userRepository;
    private final AwardPeriodRepository awardPeriodRepository;
    private final SparkService sparkService;
    private final SparkMapper sparkMapper;

    public SparkWinnerDto finalizeWinner(UUID hrUserId, FinalizeWinnerRequest request) {
        AwardPeriod period = sparkService.getPeriodOrThrow(request.awardPeriodId());
        User hrUser = getUserOrThrow(hrUserId);
        User winner = getUserOrThrow(request.winnerId());
        sparkService.getCategoryOrThrow(request.categoryId());

        long voteCount = voteRepository.countVotesForUserInCategory(
                request.awardPeriodId(), request.categoryId(), request.winnerId());

        SparkWinner sparkWinner = winnerRepository
                .findByAwardPeriodIdAndCategoryId(request.awardPeriodId(), request.categoryId())
                .orElse(SparkWinner.builder()
                        .awardPeriod(period)
                        .category(sparkService.getCategoryOrThrow(request.categoryId()))
                        .awardPoints(period.getAwardAmount())
                        .build());

        sparkWinner.setWinner(winner);
        sparkWinner.setVoteCount((int) voteCount);
        sparkWinner.setHrJustification(request.hrJustification());
        sparkWinner.setFinalizedBy(hrUser);
        sparkWinner.setFinalizedAt(LocalDateTime.now());

        // Mark the winning nomination
        nominationRepository.findByAwardPeriodIdAndCategoryId(request.awardPeriodId(), request.categoryId())
                .forEach(n -> {
                    if (n.getNominee().getId().equals(request.winnerId())) {
                        n.setStatus(NominationStatus.WINNER);
                    } else {
                        n.setStatus(NominationStatus.NOT_SELECTED);
                    }
                    nominationRepository.save(n);
                });

        SparkWinner saved = winnerRepository.save(sparkWinner);
        long nominationCount = nominationRepository.countByPeriodAndNomineeAndCategory(
                request.awardPeriodId(), request.winnerId(), request.categoryId());
        return sparkMapper.toDto(saved, 0, nominationCount);
    }

    public void announceWinners(UUID periodId) {
        AwardPeriod period = sparkService.getPeriodOrThrow(periodId);
        List<SparkWinner> winners = winnerRepository.findByAwardPeriodId(periodId);

        long categoriesWithNominations = nominationRepository.countDistinctCategoriesWithNominations(periodId);
        long finalized = winners.stream().filter(w -> w.getFinalizedAt() != null).count();
        if (categoriesWithNominations > 0 && finalized < categoriesWithNominations) {
            throw new IllegalStateException("All categories must be finalized before announcing");
        }

        LocalDateTime now = LocalDateTime.now();
        winners.forEach(w -> w.setAnnouncedAt(now));
        winnerRepository.saveAll(winners);

        period.setStatus(AwardPeriodStatus.ANNOUNCED);
        awardPeriodRepository.save(period);
        // Push notifications are a stub — implement in future
    }

    @Transactional(readOnly = true)
    public List<SparkWinnerDto> getWinners(UUID periodId) {
        List<SparkWinner> winners = winnerRepository.findByAwardPeriodId(periodId);
        if (winners.isEmpty()) return List.of();

        List<UUID> winnerIds = winners.stream().map(SparkWinner::getId).toList();
        Map<UUID, Long> countByWinner = new HashMap<>();
        for (Object[] row : congratulationRepository.countGroupedBySparkWinnerIds(winnerIds)) {
            countByWinner.put((UUID) row[0], (Long) row[1]);
        }

        return winners.stream()
                .map(w -> sparkMapper.toDto(
                        w,
                        countByWinner.getOrDefault(w.getId(), 0L).intValue(),
                        nominationRepository.countByPeriodAndNomineeAndCategory(
                                periodId, w.getWinner().getId(), w.getCategory().getId())))
                .toList();
    }

    public SparkCongratulationDto congratulate(UUID winnerId, UUID userId, CongratulateRequest request) {
        SparkWinner winner = winnerRepository.findById(winnerId)
                .orElseThrow(() -> new IllegalArgumentException("Winner not found: " + winnerId));
        User user = getUserOrThrow(userId);

        if (congratulationRepository.existsBySparkWinnerIdAndUserId(winnerId, userId)) {
            throw new IllegalStateException("You have already congratulated this winner");
        }

        SparkCongratulation congratulation = SparkCongratulation.builder()
                .sparkWinner(winner)
                .awardPeriod(winner.getAwardPeriod())
                .user(user)
                .reaction(request.reaction())
                .message(request.message())
                .build();

        return sparkMapper.toDto(congratulationRepository.save(congratulation));
    }

    @Transactional(readOnly = true)
    public List<SparkCongratulationDto> getCongratulations(UUID winnerId) {
        return congratulationRepository.findBySparkWinnerId(winnerId)
                .stream().map(sparkMapper::toDto).toList();
    }

    private User getUserOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }
}
