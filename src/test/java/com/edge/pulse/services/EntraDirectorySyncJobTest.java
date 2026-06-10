package com.edge.pulse.services;

import com.edge.pulse.data.dto.DirectorySyncResultDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntraDirectorySyncJobTest {

    @Mock
    EntraDirectorySyncService syncService;

    @InjectMocks
    EntraDirectorySyncJob job;

    @Test
    void runSync_delegatesToService() {
        when(syncService.syncDirectory()).thenReturn(
                new DirectorySyncResultDto(10, 2, 5, 1, 3, 1, 0, 0, LocalDateTime.now()));

        job.runSync();

        verify(syncService).syncDirectory();
    }

    @Test
    void runSync_logsResult() {
        DirectorySyncResultDto result =
                new DirectorySyncResultDto(50, 10, 38, 2, 5, 3, 2, 1, LocalDateTime.now());
        when(syncService.syncDirectory()).thenReturn(result);

        // Just verify no exception is thrown and service is called
        job.runSync();

        verify(syncService).syncDirectory();
    }
}
