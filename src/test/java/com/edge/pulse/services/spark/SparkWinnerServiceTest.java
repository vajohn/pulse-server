package com.edge.pulse.services.spark;

import com.edge.pulse.data.dto.spark.SparkWinnerDto;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.spark.SparkCategory;
import com.edge.pulse.data.models.spark.SparkWinner;
import com.edge.pulse.mappers.SparkMapper;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.repositories.spark.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SparkWinnerServiceTest {

    @Mock SparkWinnerRepository winnerRepository;
    @Mock SparkCongratulationRepository congratulationRepository;
    @Mock NominationRepository nominationRepository;
    @Mock LeaderVoteRepository voteRepository;
    @Mock UserRepository userRepository;
    @Mock AwardPeriodRepository awardPeriodRepository;
    @Mock SparkService sparkService;
    @Mock SparkMapper sparkMapper;

    @InjectMocks SparkWinnerService service;

    @Test
    void getWinners_computesAndPassesNominationCountToMapper() {
        UUID periodId = UUID.randomUUID();
        UUID winnerRowId = UUID.randomUUID();
        UUID winnerUserId = UUID.randomUUID();

        SparkWinner w = mock(SparkWinner.class);
        User winnerUser = mock(User.class);
        SparkCategory cat = mock(SparkCategory.class);

        when(w.getId()).thenReturn(winnerRowId);
        when(w.getWinner()).thenReturn(winnerUser);
        when(winnerUser.getId()).thenReturn(winnerUserId);
        when(w.getCategory()).thenReturn(cat);
        when(cat.getId()).thenReturn("PROACTIVE");

        when(winnerRepository.findByAwardPeriodId(periodId)).thenReturn(List.of(w));
        when(congratulationRepository.countGroupedBySparkWinnerIds(anyList())).thenReturn(List.of());
        when(nominationRepository.countByPeriodAndNomineeAndCategory(periodId, winnerUserId, "PROACTIVE"))
                .thenReturn(5L);
        when(sparkMapper.toDto(any(SparkWinner.class), anyLong(), anyLong()))
                .thenReturn(mock(SparkWinnerDto.class));

        service.getWinners(periodId);

        verify(sparkMapper).toDto(eq(w), eq(0L), eq(5L));
    }
}
