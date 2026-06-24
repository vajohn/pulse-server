package com.edge.pulse.services.psychometric.assets;

import com.edge.pulse.data.models.psychometric.PsychometricAsset;
import com.edge.pulse.repositories.psychometric.PsychometricAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class AssetService {
    static final int MAX_BYTES = 5 * 1024 * 1024; // §10 guardrail
    private final PsychometricAssetRepository repo;

    public AssetService(PsychometricAssetRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public PsychometricAsset store(byte[] bytes, String contentType, String originalName, String locale) {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("Empty asset: " + originalName);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Asset " + originalName + " exceeds " + MAX_BYTES + " bytes");
        String sha = sha256(bytes);
        Optional<PsychometricAsset> existing = repo.findBySha256(sha);
        if (existing.isPresent()) return existing.get();
        return repo.save(PsychometricAsset.builder()
                .id(UUID.randomUUID()).sha256(sha).contentType(contentType)
                .byteSize(bytes.length).locale(locale).data(bytes).originalName(originalName)
                .build());
    }

    @Transactional(readOnly = true)
    public Optional<PsychometricAsset> find(UUID id) {
        return repo.findById(id);
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
