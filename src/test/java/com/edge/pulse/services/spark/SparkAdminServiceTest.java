package com.edge.pulse.services.spark;

import com.edge.pulse.data.dto.spark.AwardPeriodDto;
import com.edge.pulse.data.enums.AwardPeriodStatus;
import com.edge.pulse.data.models.spark.AwardPeriod;
import com.edge.pulse.mappers.SparkMapper;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.repositories.spark.AwardPeriodRepository;
import com.edge.pulse.repositories.spark.NominationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SparkAdminServiceTest {

    @Mock AwardPeriodRepository awardPeriodRepository;
    @Mock NominationRepository nominationRepository;
    @Mock UserRepository userRepository;
    @Mock SparkMapper sparkMapper;

    @InjectMocks SparkAdminService service;

    private static final UUID PERIOD_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private AwardPeriod periodWithStatus(AwardPeriodStatus status) {
        AwardPeriod p = new AwardPeriod();
        p.setId(PERIOD_ID);
        p.setName("Test Period");
        p.setNominationStart(LocalDateTime.now());
        p.setNominationEnd(LocalDateTime.now().plusDays(7));
        p.setVotingStart(LocalDateTime.now().plusDays(8));
        p.setVotingEnd(LocalDateTime.now().plusDays(14));
        p.setStatus(status);
        return p;
    }

    private AwardPeriodDto dtoWithStatus(AwardPeriodStatus status) {
        return new AwardPeriodDto(PERIOD_ID, "Test Period",
                LocalDateTime.now(), LocalDateTime.now().plusDays(7),
                LocalDateTime.now().plusDays(8), LocalDateTime.now().plusDays(14),
                status, null, null, LocalDateTime.now());
    }

    // ── cancelPeriod — cancellable states ─────────────────────────────────────

    @ParameterizedTest
    @EnumSource(value = AwardPeriodStatus.class, names = {
            "UPCOMING", "NOMINATION_OPEN", "NOMINATION_CLOSED", "VOTING_OPEN", "VOTING_CLOSED"
    })
    void cancelPeriod_fromCancellableStatus_setsStatusCancelled(AwardPeriodStatus status) {
        AwardPeriod period = periodWithStatus(status);
        when(awardPeriodRepository.findById(PERIOD_ID)).thenReturn(Optional.of(period));
        when(awardPeriodRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sparkMapper.toDto(any(AwardPeriod.class))).thenAnswer(inv ->
                dtoWithStatus(((AwardPeriod) inv.getArgument(0)).getStatus()));

        AwardPeriodDto result = service.cancelPeriod(PERIOD_ID);

        assertThat(result.status()).isEqualTo(AwardPeriodStatus.CANCELLED);
        assertThat(period.getStatus()).isEqualTo(AwardPeriodStatus.CANCELLED);
    }

    // ── cancelPeriod — terminal states throw ──────────────────────────────────

    @ParameterizedTest
    @EnumSource(value = AwardPeriodStatus.class, names = {
            "FINALIZED", "ANNOUNCED", "EXPIRED", "CANCELLED"
    })
    void cancelPeriod_fromNonCancellableStatus_throwsIllegalState(AwardPeriodStatus status) {
        when(awardPeriodRepository.findById(PERIOD_ID))
                .thenReturn(Optional.of(periodWithStatus(status)));

        assertThatThrownBy(() -> service.cancelPeriod(PERIOD_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot cancel a period in status");
    }

    @Test
    void cancelPeriod_periodNotFound_throwsIllegalArgument() {
        when(awardPeriodRepository.findById(PERIOD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelPeriod(PERIOD_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Award period not found");
    }

    // ── advancePeriodStatus ────────────────────────────────────────────────────

    @Test
    void advancePeriodStatus_fromUpcoming_advancesToNominationOpen() {
        AwardPeriod period = periodWithStatus(AwardPeriodStatus.UPCOMING);
        when(awardPeriodRepository.findById(PERIOD_ID)).thenReturn(Optional.of(period));
        when(awardPeriodRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sparkMapper.toDto(any(AwardPeriod.class))).thenAnswer(inv ->
                dtoWithStatus(((AwardPeriod) inv.getArgument(0)).getStatus()));

        AwardPeriodDto result = service.advancePeriodStatus(PERIOD_ID);

        assertThat(result.status()).isEqualTo(AwardPeriodStatus.NOMINATION_OPEN);
    }

    @Test
    void advancePeriodStatus_fromAnnounced_advancesToExpired() {
        AwardPeriod period = periodWithStatus(AwardPeriodStatus.ANNOUNCED);
        when(awardPeriodRepository.findById(PERIOD_ID)).thenReturn(Optional.of(period));
        when(awardPeriodRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sparkMapper.toDto(any(AwardPeriod.class))).thenAnswer(inv ->
                dtoWithStatus(((AwardPeriod) inv.getArgument(0)).getStatus()));

        AwardPeriodDto result = service.advancePeriodStatus(PERIOD_ID);

        assertThat(result.status()).isEqualTo(AwardPeriodStatus.EXPIRED);
    }

    @Test
    void advancePeriodStatus_fromCancelled_throwsIllegalState() {
        when(awardPeriodRepository.findById(PERIOD_ID))
                .thenReturn(Optional.of(periodWithStatus(AwardPeriodStatus.CANCELLED)));

        assertThatThrownBy(() -> service.advancePeriodStatus(PERIOD_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot advance status from");
    }

    @Test
    void advancePeriodStatus_periodNotFound_throwsIllegalArgument() {
        when(awardPeriodRepository.findById(PERIOD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.advancePeriodStatus(PERIOD_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Award period not found");
    }
}
