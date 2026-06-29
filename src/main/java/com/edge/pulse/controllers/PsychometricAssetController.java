package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.psychometric.AssetRefDto;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/psychometric/assets")
public class PsychometricAssetController {
    private final AssetService assetService;
    private final BlobStore blobStore;

    private static final Set<String> ALLOWED = Set.of("image/png", "image/jpeg", "image/webp");

    public PsychometricAssetController(AssetService assetService, BlobStore blobStore) {
        this.assetService = assetService;
        this.blobStore = blobStore;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ASSESS_UPDATE')")
    public ResponseEntity<AssetRefDto> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam(value = "locale", required = false) String locale) {
        if (file == null || file.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty file");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unreadable file");
        }
        String declared = file.getContentType();
        String sniffed = sniffImageType(bytes);
        if (declared == null || !ALLOWED.contains(declared) || sniffed == null || !sniffed.equals(declared))
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Only PNG, JPEG or WEBP images are allowed");
        try {
            PsychometricAsset a = assetService.store(bytes, declared, file.getOriginalFilename(),
                    (locale != null && !locale.isBlank()) ? locale : null);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new AssetRefDto(a.getId(), "/api/psychometric/assets/" + a.getId(),
                            a.getContentType(), a.getByteSize()));
        } catch (IllegalArgumentException e) { // size guard etc.
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    /** Minimal magic-byte sniff — returns the canonical content-type or null. */
    static String sniffImageType(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47)
            return "image/png";
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF)
            return "image/jpeg";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P')
            return "image/webp";
        return null;
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
