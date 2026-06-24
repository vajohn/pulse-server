package com.edge.pulse.services.psychometric.assets;

import com.edge.pulse.data.models.psychometric.PsychometricAsset;
import org.springframework.stereotype.Component;

@Component
public class PostgresBlobStore implements BlobStore {
    public byte[] read(PsychometricAsset asset) {
        if (asset.getData() == null)
            throw new IllegalStateException("Asset " + asset.getId() + " has no inline data (external store not configured)");
        return asset.getData();
    }
}
