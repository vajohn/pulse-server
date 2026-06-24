package com.edge.pulse.services.psychometric.assets;

import com.edge.pulse.data.models.psychometric.PsychometricAsset;

/** Returns the bytes for an asset. Postgres impl reads data; a future external impl reads storage_uri. */
public interface BlobStore {
    byte[] read(PsychometricAsset asset);
}
