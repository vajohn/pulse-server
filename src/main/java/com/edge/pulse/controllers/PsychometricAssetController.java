package com.edge.pulse.controllers;

import com.edge.pulse.data.models.psychometric.PsychometricAsset;
import com.edge.pulse.services.psychometric.assets.AssetService;
import com.edge.pulse.services.psychometric.assets.BlobStore;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/psychometric/assets")
public class PsychometricAssetController {
    private final AssetService assetService;
    private final BlobStore blobStore;

    public PsychometricAssetController(AssetService assetService, BlobStore blobStore) {
        this.assetService = assetService;
        this.blobStore = blobStore;
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> get(@PathVariable UUID id,
                                      @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        PsychometricAsset asset = assetService.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String etag = "\"" + asset.getSha256() + "\"";
        if (etag.equals(ifNoneMatch)) return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        byte[] bytes = blobStore.read(asset);
        return ResponseEntity.ok()
                .eTag(etag)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400, immutable")
                .contentType(MediaType.parseMediaType(asset.getContentType()))
                .contentLength(bytes.length)
                .body(bytes);
    }
}
