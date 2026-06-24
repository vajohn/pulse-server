package com.edge.pulse.services.psychometric.assets;

import com.edge.pulse.data.models.psychometric.PsychometricAsset;
import com.edge.pulse.repositories.psychometric.PsychometricAssetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {
    @Mock PsychometricAssetRepository repo;
    @InjectMocks AssetService service;

    @Test
    void store_newAsset_persistsWithSha256AndContentType() {
        when(repo.findBySha256(any())).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 1, 2, 3};
        PsychometricAsset a = service.store(png, "image/png", "Q1.png", "en");
        assertThat(a.getSha256()).hasSize(64);
        assertThat(a.getByteSize()).isEqualTo(png.length);
        assertThat(a.getContentType()).isEqualTo("image/png");
        assertThat(a.getData()).isEqualTo(png);
    }

    @Test
    void store_duplicateBytes_returnsExistingNoSecondSave() {
        PsychometricAsset existing = PsychometricAsset.builder().id(java.util.UUID.randomUUID()).build();
        when(repo.findBySha256(any())).thenReturn(Optional.of(existing));
        PsychometricAsset a = service.store(new byte[]{1, 2, 3}, "image/png", "Q1.png", "en");
        assertThat(a).isSameAs(existing);
        verify(repo, never()).save(any());
    }

    @Test
    void store_oversize_throws() {
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        assertThatThrownBy(() -> service.store(big, "image/png", "big.png", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds");
    }
}
