package com.edge.pulse.services.spark;

import com.edge.pulse.data.dto.PagedResponse;
import com.edge.pulse.data.dto.spark.AwardPeriodDto;
import com.edge.pulse.data.enums.AwardPeriodStatus;
import com.edge.pulse.data.models.spark.AwardPeriod;
import com.edge.pulse.mappers.SparkMapper;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.repositories.spark.AwardPeriodRepository;
import com.edge.pulse.repositories.spark.NominationRepository;
import com.edge.pulse.repositories.spark.SparkCategoryRepository;
import com.edge.pulse.repositories.spark.SparkWinnerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SparkServicePaginationTest {

    @Mock AwardPeriodRepository awardPeriodRepository;
    @Mock SparkCategoryRepository categoryRepository;
    @Mock NominationRepository nominationRepository;
    @Mock SparkWinnerRepository winnerRepository;
    @Mock UserRepository userRepository;
    @Mock SparkMapper sparkMapper;

    @InjectMocks SparkService service;

    // ── helpers ───────────────────────────────────────────────────────────────

    private AwardPeriod period(AwardPeriodStatus status) {
        AwardPeriod p = new AwardPeriod();
        p.setId(UUID.randomUUID());
        p.setName("Period");
        p.setNominationStart(LocalDateTime.now());
        p.setNominationEnd(LocalDateTime.now().plusDays(7));
        p.setVotingStart(LocalDateTime.now().plusDays(8));
        p.setVotingEnd(LocalDateTime.now().plusDays(14));
        p.setStatus(status);
        return p;
    }

    private AwardPeriodDto dto(AwardPeriodStatus status) {
        return new AwardPeriodDto(UUID.randomUUID(), "Period",
                LocalDateTime.now(), LocalDateTime.now().plusDays(7),
                LocalDateTime.now().plusDays(8), LocalDateTime.now().plusDays(14),
                status, null, null, LocalDateTime.now());
    }

    /** Build a Page that signals hasNext()=true (total > page*size + size). */
    private Page<AwardPeriod> pageWith(int itemCount, boolean hasNext) {
        List<AwardPeriod> items = IntStream.range(0, itemCount)
                .mapToObj(i -> period(AwardPeriodStatus.UPCOMING))
                .toList();
        long total = hasNext ? (long) itemCount + 1 : itemCount;
        return new PageImpl<>(items, PageRequest.of(0, itemCount == 0 ? 20 : itemCount), total);
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void getPagedPeriods_noFilter_page0_hasMoreTrue() {
        Page<AwardPeriod> fakePage = pageWith(20, true);
        when(awardPeriodRepository.findAllByOrderByNominationStartDesc(any(Pageable.class)))
                .thenReturn(fakePage);
        when(sparkMapper.toDto(any(AwardPeriod.class))).thenAnswer(inv ->
                dto(((AwardPeriod) inv.getArgument(0)).getStatus()));

        PagedResponse<AwardPeriodDto> result = service.getPagedPeriods(0, 20, null);

        assertThat(result.hasMore()).isTrue();
        assertThat(result.content()).hasSize(20);
    }

    @Test
    void getPagedPeriods_noFilter_lastPage_hasMoreFalse() {
        Page<AwardPeriod> fakePage = pageWith(5, false);
        when(awardPeriodRepository.findAllByOrderByNominationStartDesc(any(Pageable.class)))
                .thenReturn(fakePage);
        when(sparkMapper.toDto(any(AwardPeriod.class))).thenAnswer(inv ->
                dto(((AwardPeriod) inv.getArgument(0)).getStatus()));

        PagedResponse<AwardPeriodDto> result = service.getPagedPeriods(0, 20, null);

        assertThat(result.hasMore()).isFalse();
        assertThat(result.content()).hasSize(5);
    }

    @Test
    void getPagedPeriods_withStatus_callsFilteredRepo() {
        Page<AwardPeriod> fakePage = pageWith(3, false);
        when(awardPeriodRepository.findByStatusOrderByNominationStartDesc(
                eq(AwardPeriodStatus.CANCELLED), any(Pageable.class)))
                .thenReturn(fakePage);
        when(sparkMapper.toDto(any(AwardPeriod.class))).thenAnswer(inv ->
                dto(((AwardPeriod) inv.getArgument(0)).getStatus()));

        service.getPagedPeriods(0, 20, AwardPeriodStatus.CANCELLED);

        verify(awardPeriodRepository).findByStatusOrderByNominationStartDesc(
                eq(AwardPeriodStatus.CANCELLED), any(Pageable.class));
        verify(awardPeriodRepository, never())
                .findAllByOrderByNominationStartDesc(any(Pageable.class));
    }

    @Test
    void getPagedPeriods_nullStatus_callsUnfilteredRepo() {
        Page<AwardPeriod> fakePage = pageWith(2, false);
        when(awardPeriodRepository.findAllByOrderByNominationStartDesc(any(Pageable.class)))
                .thenReturn(fakePage);
        when(sparkMapper.toDto(any(AwardPeriod.class))).thenAnswer(inv ->
                dto(((AwardPeriod) inv.getArgument(0)).getStatus()));

        service.getPagedPeriods(0, 20, null);

        verify(awardPeriodRepository).findAllByOrderByNominationStartDesc(any(Pageable.class));
        verify(awardPeriodRepository, never())
                .findByStatusOrderByNominationStartDesc(any(), any());
    }

    @Test
    void getPagedPeriods_sizeIsCappedAt50() {
        when(awardPeriodRepository.findAllByOrderByNominationStartDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 50), 0));

        service.getPagedPeriods(0, 200, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(awardPeriodRepository).findAllByOrderByNominationStartDesc(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
    }
}
