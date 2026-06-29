package com.edge.pulse.mappers;

import com.edge.pulse.data.dto.CandidateAnswerDto;
import com.edge.pulse.data.models.CandidateAnswer;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FormMapperTest {

    @Test
    void candidateAnswerDto_exposesImageUrls_whenAssetSet() {
        UUID en = UUID.randomUUID();
        UUID ar = UUID.randomUUID();
        CandidateAnswer ca = CandidateAnswer.builder()
                .id(UUID.randomUUID()).label("").displayOrder(0)
                .imageAssetId(en).imageAssetIdAr(ar).build();
        CandidateAnswerDto dto = FormMapper.toCandidateAnswerDto(ca);
        assertEquals("/api/psychometric/assets/" + en, dto.imageUrl());
        assertEquals("/api/psychometric/assets/" + ar, dto.imageUrlAr());
        assertEquals(en, dto.imageAssetId());
        assertEquals(ar, dto.imageAssetIdAr());
    }

    @Test
    void candidateAnswerDto_nullImageUrls_whenNoAsset() {
        CandidateAnswer ca = CandidateAnswer.builder()
                .id(UUID.randomUUID()).label("Agree").labelAr("أوافق").displayOrder(1).build();
        CandidateAnswerDto dto = FormMapper.toCandidateAnswerDto(ca);
        assertNull(dto.imageAssetId());
        assertNull(dto.imageUrl());
        assertNull(dto.imageUrlAr());
        assertEquals("Agree", dto.label());
    }
}
