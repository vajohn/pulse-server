package com.edge.pulse.services;

import com.edge.pulse.configs.CacheTtlProperties;
import com.edge.pulse.data.models.OrganizationalUnit;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.dto.OpenSessionRequest;
import com.edge.pulse.data.models.Form;
import com.edge.pulse.data.models.ResponseSession;
import com.edge.pulse.repositories.FormRepository;
import com.edge.pulse.repositories.ResponseSessionRepository;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.services.psychometric.ScoringService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private ResponseSessionRepository responseSessionRepository;
    @Mock
    private FormRepository formRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AnonIdentityService anonIdentityService;
    @Mock
    private FormCacheService cacheService;
    @Mock
    private ScoringService scoringService;
    @Mock
    private Clock clock;
    @Mock
    private SessionCreationHelper sessionCreationHelper;
    @Spy
    private CacheTtlProperties cacheTtlProps = new CacheTtlProperties();

    @InjectMocks
    private SessionService service;

    @Test
    void openOrResumeSession_createsNewIdentifiedSession() {
        var userId = UUID.randomUUID();
        var surveyId = UUID.randomUUID();
        var user = User.builder().id(userId).email("test@test.com").azureAdId("azure1").build();
        var survey = Form.builder().id(surveyId).title("Q").anonWindowMinutes(60).build();

        when(formRepository.findById(surveyId)).thenReturn(Optional.of(survey));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        // Cache miss — no open session in cache
        when(cacheService.get(any(), eq(UUID.class))).thenReturn(Optional.empty());
        when(responseSessionRepository.findFirstByUserIdAndFormIdAndCompletedAtIsNullOrderByStartedAtDesc(userId, surveyId))
                .thenReturn(Optional.empty());
        when(sessionCreationHelper.tryCreate(any())).thenAnswer(i -> {
            ResponseSession s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        var request = new OpenSessionRequest(false);
        ResponseSession result = service.openOrResumeSession(surveyId, userId, request);

        assertThat(result).isNotNull();
        assertThat(result.isAnonymous()).isFalse();
        verify(sessionCreationHelper).tryCreate(any());
    }

    @Test
    void completeSession_setsCompletedAt() {
        var sessionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        // isAnonymous=true skips ownership check (no user link on anonymous sessions)
        var session = ResponseSession.builder().id(sessionId).isAnonymous(true).startedAt(LocalDateTime.now()).build();
        when(responseSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(responseSessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ResponseSession result = service.completeSession(sessionId, userId);

        assertThat(result.getCompletedAt()).isNotNull();
    }

    @Test
    void completeSession_throwsWhenAlreadyCompleted() {
        var sessionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var session = ResponseSession.builder().id(sessionId).isAnonymous(true).startedAt(LocalDateTime.now()).completedAt(LocalDateTime.now()).build();
        when(responseSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.completeSession(sessionId, userId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void enforceTimeLimit_exceeded_throws() {
        var sessionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        // timeLimitSecs=60, serverStartEpoch=0 → allowedMs=90_000; elapsed=120_000 → over limit
        var session = ResponseSession.builder()
                .id(sessionId).isAnonymous(true).startedAt(LocalDateTime.now())
                .timeLimitSecs(60).serverStartEpoch(0L)
                .build();
        when(responseSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(clock.instant()).thenReturn(Instant.ofEpochMilli(120_000));

        assertThatThrownBy(() -> service.completeSession(sessionId, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("time limit exceeded");
    }

    @Test
    void enforceTimeLimit_withinGrace_doesNotThrow() {
        var sessionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        // timeLimitSecs=60, serverStartEpoch=0 → allowedMs=90_000; elapsed=80_000 → within limit
        var session = ResponseSession.builder()
                .id(sessionId).isAnonymous(true).startedAt(LocalDateTime.now())
                .timeLimitSecs(60).serverStartEpoch(0L)
                .build();
        when(responseSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(clock.instant()).thenReturn(Instant.ofEpochMilli(80_000));
        when(responseSessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ResponseSession result = service.completeSession(sessionId, userId);
        assertThat(result.getCompletedAt()).isNotNull();
    }

    @Test
    void openOrResumeSession_raceCondition_returnsWinnerSession() {
        var userId = UUID.randomUUID();
        var formId = UUID.randomUUID();
        var user = User.builder().id(userId).email("x@x.com").azureAdId("a").build();
        var form = Form.builder().id(formId).title("Q").anonWindowMinutes(60).build();
        var winner = ResponseSession.builder().id(UUID.randomUUID()).form(form).user(user)
                         .isAnonymous(false).startedAt(LocalDateTime.now()).build();

        when(formRepository.findById(formId)).thenReturn(Optional.of(form));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(cacheService.get(any(), eq(UUID.class))).thenReturn(Optional.empty());
        when(responseSessionRepository
                .findFirstByUserIdAndFormIdAndCompletedAtIsNullOrderByStartedAtDesc(userId, formId))
                .thenReturn(Optional.empty());           // initial check: no session yet
        when(responseSessionRepository.findFirstOpenForUpdate(eq(userId), eq(formId), any()))
                .thenReturn(List.of(winner));            // retry after race: winner found (locked)
        when(sessionCreationHelper.tryCreate(any()))
                .thenThrow(new DataIntegrityViolationException("concurrent"));

        ResponseSession result = service.openOrResumeSession(formId, userId, new OpenSessionRequest(false));

        assertThat(result.getId()).isEqualTo(winner.getId());
        verify(responseSessionRepository, never()).save(any());
    }
}
