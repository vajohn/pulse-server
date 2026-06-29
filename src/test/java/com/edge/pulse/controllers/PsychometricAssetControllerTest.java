package com.edge.pulse.controllers;

import com.edge.pulse.data.models.psychometric.PsychometricAsset;
import com.edge.pulse.services.psychometric.assets.AssetService;
import com.edge.pulse.services.psychometric.assets.BlobStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PsychometricAssetControllerTest {
    MockMvc mvc;
    AssetService assetService = mock(AssetService.class);
    BlobStore blobStore = mock(BlobStore.class);

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PsychometricAssetController(assetService, blobStore)).build();
    }

    @Test
    void serves_png_with_etag() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
        var asset = PsychometricAsset.builder().id(id).sha256("abc123").contentType("image/png").byteSize(png.length).build();
        when(assetService.find(id)).thenReturn(Optional.of(asset));
        when(blobStore.read(asset)).thenReturn(png);
        mvc.perform(get("/api/psychometric/assets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"abc123\""))
                .andExpect(header().string("Content-Type", "image/png"));
    }

    @Test
    void returns_304_when_etag_matches() throws Exception {
        UUID id = UUID.randomUUID();
        var asset = PsychometricAsset.builder().id(id).sha256("abc123").contentType("image/png").byteSize(4).build();
        when(assetService.find(id)).thenReturn(Optional.of(asset));
        mvc.perform(get("/api/psychometric/assets/{id}", id).header("If-None-Match", "\"abc123\""))
                .andExpect(status().isNotModified());
    }

    @Test
    void returns_404_when_missing() throws Exception {
        UUID id = UUID.randomUUID();
        when(assetService.find(id)).thenReturn(Optional.empty());
        mvc.perform(get("/api/psychometric/assets/{id}", id)).andExpect(status().isNotFound());
    }

    // ── upload (POST) ─────────────────────────────────────────────────────────

    @Test
    void upload_png_returns201_withUrlAndId() throws Exception {
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
        MockMultipartFile file = new MockMultipartFile("file", "Q1.png", "image/png", png);
        when(assetService.store(any(byte[].class), eq("image/png"), eq("Q1.png"), isNull()))
                .thenReturn(PsychometricAsset.builder()
                        .id(UUID.fromString("00000000-0000-0000-0000-0000000000aa"))
                        .sha256("abc").contentType("image/png").byteSize(png.length).build());

        mvc.perform(multipart("/api/psychometric/assets").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("00000000-0000-0000-0000-0000000000aa"))
                .andExpect(jsonPath("$.url").value("/api/psychometric/assets/00000000-0000-0000-0000-0000000000aa"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.byteSize").value(png.length));
    }

    @Test
    void upload_rejectsNonImageType_415() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "x.txt", "text/plain", new byte[]{1, 2, 3});
        mvc.perform(multipart("/api/psychometric/assets").file(file))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void upload_rejectsSpoofedContentType_415() throws Exception {
        // declares png but bytes are not png
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", new byte[]{0, 0, 0, 0});
        mvc.perform(multipart("/api/psychometric/assets").file(file))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void upload_emptyFile_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);
        mvc.perform(multipart("/api/psychometric/assets").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_oversize_maps422() throws Exception {
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", png);
        when(assetService.store(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Asset big.png exceeds 5242880 bytes"));
        mvc.perform(multipart("/api/psychometric/assets").file(file))
                .andExpect(status().isUnprocessableEntity());
    }
}
