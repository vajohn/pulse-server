package com.edge.pulse.services;

import com.edge.pulse.data.dto.AuditLogDto;
import com.edge.pulse.data.models.AuditLog;
import com.edge.pulse.data.models.User;
import com.edge.pulse.mappers.AuditMapper;
import com.edge.pulse.repositories.AuditLogRepository;
import com.edge.pulse.repositories.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final AuditMapper auditMapper;
    private final ObjectMapper objectMapper;

    private static final int MAX_PAGE_SIZE = 100;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(UUID userId, String action, String entityType, UUID entityId, String details, String ipAddress) {
        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;

        AuditLog auditLog = AuditLog.builder()
                .user(user)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(details)
                .ipAddress(ipAddress)
                .build();

        auditLogRepository.save(auditLog);
        log.debug("Audit log: {} on {} {} by user {}", action, entityType, entityId, userId);
    }

    /**
     * Builds a JSON detail string from key-value pairs. Safer than string concatenation.
     * Example: buildDetail("title", "My Survey") → {"title":"My Survey"}
     */
    public String buildDetail(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("buildDetail requires an even number of arguments (key-value pairs)");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize audit detail", e);
            return "{}";
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> getAuditLogs(UUID userId, String entityType, int page, int size) {
        int clampedSize = Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, clampedSize);

        Page<AuditLog> logs;
        if (userId != null && entityType != null) {
            logs = auditLogRepository.findByUserIdAndEntityTypeOrderByCreatedAtDesc(userId, entityType, pageable);
        } else if (userId != null) {
            logs = auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        } else if (entityType != null) {
            logs = auditLogRepository.findByEntityTypeOrderByCreatedAtDesc(entityType, pageable);
        } else {
            logs = auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        return logs.map(auditMapper::toDto);
    }
}
