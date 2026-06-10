package com.edge.pulse.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FormCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                return Optional.of(objectMapper.readValue(json, type));
            }
        } catch (Exception e) {
            log.warn("Cache read failed for key {}: {}", key, e.getMessage());
        }
        return Optional.empty();
    }

    public <T> Optional<T> get(String key, TypeReference<T> typeRef) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                return Optional.of(objectMapper.readValue(json, typeRef));
            }
        } catch (Exception e) {
            log.warn("Cache read failed for key {}: {}", key, e.getMessage());
        }
        return Optional.empty();
    }

    public void put(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (Exception e) {
            log.warn("Cache write failed for key {}: {}", key, e.getMessage());
        }
    }

    public void evict(String key) {
        redisTemplate.delete(key);
    }

    // Key builders
    public static String anonTokenKey(String token) {
        return "anon:token:" + token;
    }

    public static String activeQuestionsKey(java.util.UUID formId) {
        return "form:active-questions:" + formId;
    }

    public static String openSessionKey(java.util.UUID userId, java.util.UUID formId) {
        return "session:open:" + userId + ":" + formId;
    }

    public static String orgDescendantsKey(java.util.UUID orgUnitId) {
        return "org-unit:descendants:" + orgUnitId;
    }

    public static String userAssignmentsKey(java.util.UUID userId) {
        // v3: bumped when MyAssignmentDto field names changed (surveyId→formId, instrumentType→formType)
        return "assignments:v3:user:" + userId;
    }

    public void evictByPattern(String pattern) {
        try {
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(200).build();
            List<String> keys = new ArrayList<>();
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                cursor.forEachRemaining(keys::add);
            }
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Cache eviction failed for pattern {}: {}", pattern, e.getMessage());
        }
    }
}
