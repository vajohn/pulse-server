package com.edge.pulse.services;

import com.edge.pulse.data.models.AuditLog;
import com.edge.pulse.data.models.User;
import com.edge.pulse.mappers.AuditMapper;
import com.edge.pulse.repositories.AuditLogRepository;
import com.edge.pulse.repositories.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditMapper auditMapper;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditLogRepository, userRepository, auditMapper, new ObjectMapper());
    }

    @Test
    void logAction_withUserId_savesLog() {
        UUID userId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        User user = User.builder().id(userId).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        auditService.logAction(userId, "LOGIN", "session", entityId, "{}", "127.0.0.1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo("LOGIN");
        assertThat(saved.getEntityType()).isEqualTo("session");
        assertThat(saved.getEntityId()).isEqualTo(entityId);
        assertThat(saved.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(saved.getUser()).isEqualTo(user);
    }

    @Test
    void logAction_withNullUserId_savesLogWithoutUser() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        auditService.logAction(null, "SYSTEM_EVENT", "system", null, null, "0.0.0.0");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        assertThat(captor.getValue().getUser()).isNull();
        assertThat(captor.getValue().getAction()).isEqualTo("SYSTEM_EVENT");
    }
}
