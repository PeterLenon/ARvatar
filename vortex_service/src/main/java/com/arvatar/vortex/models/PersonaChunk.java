package com.arvatar.vortex.models;

import java.util.UUID;

public class PersonaChunk {
    public final UUID chunkId;
    public final String transcript;
    public final UUID videoId;
    public final double distance;

    public PersonaChunk(UUID chunkId, String transcript, UUID videoId, double distance) {
        this.chunkId = chunkId;
        this.transcript = transcript;
        this.videoId = videoId;
        this.distance = distance;
    }
}

