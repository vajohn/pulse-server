package com.edge.pulse.services.spark;

import com.edge.pulse.data.dto.spark.CastVoteRequest;
import com.edge.pulse.data.dto.spark.LeaderVoteDto;
import com.edge.pulse.data.enums.AwardPeriodStatus;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.spark.AwardPeriod;
import com.edge.pulse.data.models.spark.LeaderVote;
import com.edge.pulse.data.models.spark.Nomination;
import com.edge.pulse.mappers.SparkMapper;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.repositories.spark.LeaderVoteRepository;
import com.edge.pulse.repositories.spark.NominationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SparkVoteService {

    private final LeaderVoteRepository voteRepository;
    private final NominationRepository nominationRepository;
    private final UserRepository userRepository;
    private final SparkService sparkService;
    private final SparkMapper sparkMapper;

    public LeaderVoteDto castVote(UUID leaderId, CastVoteRequest request) {
        AwardPeriod period = sparkService.getPeriodOrThrow(request.awardPeriodId());
        if (period.getStatus() != AwardPeriodStatus.VOTING_OPEN) {
            throw new IllegalStateException("Voting is not open for this period");
        }

        User leader = getUserOrThrow(leaderId);
        sparkService.getCategoryOrThrow(request.categoryId());

        Nomination nomination = nominationRepository.findById(request.nominationId())
                .orElseThrow(() -> new IllegalArgumentException("Nomination not found: " + request.nominationId()));

        // Update existing vote or create new one
        LeaderVote vote = voteRepository
                .findByAwardPeriodIdAndCategoryIdAndLeaderId(request.awardPeriodId(), request.categoryId(), leaderId)
                .orElse(LeaderVote.builder()
                        .awardPeriod(period)
                        .category(sparkService.getCategoryOrThrow(request.categoryId()))
                        .leader(leader)
                        .build());

        vote.setNominee(nomination);
        vote.setEndorsementComment(request.endorsementComment());

        return sparkMapper.toDto(voteRepository.save(vote));
    }

    @Transactional(readOnly = true)
    public List<LeaderVoteDto> getMyVotes(UUID leaderId, UUID periodId) {
        return voteRepository.findByLeaderIdAndAwardPeriodId(leaderId, periodId)
                .stream().map(sparkMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<LeaderVoteDto> getAllVotesForPeriod(UUID periodId) {
        return voteRepository.findByAwardPeriodId(periodId)
                .stream().map(sparkMapper::toDto).toList();
    }

    private User getUserOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }
}
