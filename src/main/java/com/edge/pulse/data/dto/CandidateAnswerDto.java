package com.edge.pulse.data.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

public record CandidateAnswerDto(
    UUID id,
    String label,
    /** Arabic translation of {@link #label}. Null when not yet translated. */
    String labelAr,
    int displayOrder,
    /** Image asset id (EN/default) — input on write, echoed on read. Null = text-only. */
    @JsonInclude(JsonInclude.Include.NON_NULL) UUID imageAssetId,
    @JsonInclude(JsonInclude.Include.NON_NULL) UUID imageAssetIdAr,
    /** Resolved served URL (read-only). Null when no image. */
    @JsonInclude(JsonInclude.Include.NON_NULL) String imageUrl,
    @JsonInclude(JsonInclude.Include.NON_NULL) String imageUrlAr
) {
    /** Convenience builder for read paths (computes URLs from ids). */
    public static CandidateAnswerDto of(UUID id, String label, String labelAr, int displayOrder,
                                        UUID imageAssetId, UUID imageAssetIdAr) {
        return new CandidateAnswerDto(id, label, labelAr, displayOrder, imageAssetId, imageAssetIdAr,
            imageAssetId == null ? null : "/api/psychometric/assets/" + imageAssetId,
            imageAssetIdAr == null ? null : "/api/psychometric/assets/" + imageAssetIdAr);
    }
}
