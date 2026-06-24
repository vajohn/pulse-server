package com.edge.pulse.controllers;

import com.edge.pulse.data.models.psychometric.PsychometricAsset;
import com.edge.pulse.services.psychometric.assets.AssetService;
import com.edge.pulse.services.psychometric.assets.BlobStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
}
