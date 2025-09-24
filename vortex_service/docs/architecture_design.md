## High-level: components & contracts
#### ASR - Automatic Speech Recognition
- Converts spoken audio into text. 
- ASR takes the video’s audio track and produces a transcript aligned with timestamps. 
- Those timestamps later get matched to phonemes (the sounds of speech).

#### LAZ -Compressed LAS format
- LAS = an open standard binary format for storing 3D point cloud data. 
- LAZ = the lossless compressed version of LAS (using LASzip). 
- Much smaller size than raw LAS, but still precise. 
- Widely supported by tools like PDAL, CloudCompare, Potree, etc.

#### COPC - Cloud Optimized Point Cloud
- A newer specification that organizes LAZ point cloud data in a spatial hierarchy (octree). 
- Designed for streaming from object storage like S3. 
- Lets clients request only the parts of the point cloud they need (specific tiles / resolution), rather than downloading the whole file. 
- Very efficient for edge devices like your Vortex Guru since you can cache selectively.
- 
```mermaid
flowchart LR
  %% --- Device ---
  subgraph DEV["Vortex Device (Edge)"]
    D1["Mic / Camera"]
    D2["Local ASR (opt)"]
    D3["TTS + Viseme Scheduler"]
    D4["Pointcloud Cache"]
  end
  D1 --> D2

  %% --- API ---
  subgraph API_SG["Service API (stateless)"]
    A1["Issue Presigned URLs"]
    A2["Return Manifest / Registry"]
    A3["Route to Dialog Engine"]
  end

  %% --- Workers ---
  subgraph WRK["Background Workers"]
    W1["ASR + Alignment"]
    W2["Photogrammetry / PC Build"]
    W3["Map + Manifest Builder"]
  end

  %% --- S3 Store ---
  subgraph S3_SG["MinIO (S3-compatible, on-prem)"]
    S1["(videos/...)"]
    S2["(analytics/*.parquet)"]
    S3PC["(pointclouds/guru/ver/...)"]
    S4MF["(gurus/guru/ver/manifest.json)"]
    S5RG["(gurus/guru/registry.json)"]
    S6JB["(jobs/stage/*.json)"]
  end

  %% Upload & processing
  D2 -->|Upload via presigned PUT| S1
  S1 -->|Bucket notifications| W1
  W1 -->|phonemes / transcript parquet| S2
  W1 -->|enqueue| W2
  W2 -->|COPC / LAZ staging| S3PC
  W2 -->|enqueue| W3
  W3 -->|phoneme_map + manifest| S4MF
  W3 -->|publish set current| S5RG

  %% Runtime fetch
  D2 -->|Get registry / manifest| A2
  A2 -->|Return manifest URI| D2
  D2 -->|GET assets| S3PC

  %% Dialog path
  D2 -->|Question `text or audio`| A3
  A3 -->|Dialog text to phonetics| D2

  %% Jobs audit
  S6JB -. audit / status .- A1
```

## Pipeline A — Creator upload → Build point-cloud guru

```mermaid
sequenceDiagram
  autonumber
  participant CreatorApp as Creator App
  participant API as Service API
  participant S3 as MinIO (S3)
  participant NQ as Bucket Notif → Queue
  participant ASR as Worker: ASR+Align
  participant PC as Worker: Photogrammetry
  participant MM as Worker: Map+Manifest

  CreatorApp->>API: Request upload session (guru_id)
  API-->>CreatorApp: Presigned PUT (videos/{video_id}/raw.mp4)
  CreatorApp->>S3: PUT raw.mp4
  S3-->>NQ: ObjectCreated event (videos/...)
  NQ->>ASR: Enqueue ASR job (jobs/ingest.json → running)
  ASR->>S3: Write analytics/{video_id}/phonemes.parquet + transcript.parquet
  ASR-->>S3: Update jobs/asr/{job_id}.json → succeeded
  ASR->>NQ: Enqueue photogrammetry
  NQ->>PC: Start PC build per viseme (staging prefix)
  PC->>S3: Write pointclouds/{guru}/_staging/{version}/... .copc.laz
  PC-->>S3: jobs/photogrammetry/{job}.json → succeeded
  PC->>NQ: Enqueue map/manifest
  NQ->>MM: Build phoneme_map + manifest
  MM->>S3: Write .../_staging/{version}/maps/ & manifest.json
  MM->>S3: Promote to {version}/ (copy+delete), set registry.current
  MM-->>S3: jobs/publish/{job}.json → succeeded

```

## Pipeline B — Customer runtime (run the guru)
```mermaid
sequenceDiagram
  autonumber
  participant Device as Vortex Device
  participant API as Service API
  participant S3 as MinIO (S3)
  participant Dialog as Dialog Engine

  Device->>API: Start session (guru_id)
  API-->>Device: Manifest URI (current version)
  Device->>S3: GET manifest.json
  Device->>S3: Download COPC/LAZ + phoneme_map.json (parallel)
  Device-->>Device: Verify checksums cache show neutral
  Device->>API: User question (text + optional audio)
  API->>Dialog: Ask question
  Dialog-->>API: Answer text + phonetics (timings)
  API-->>Device: Text + phonetics
  Device-->>Device: TTS audio schedule visemes render lipsync
```

## Pipeline C — Service provider ops & health
```mermaid
flowchart TD
  subgraph Ops ["Ops & Health"]
    O1["Dashboards<br/>(p95s, error rates)"]
    O2["Alarms<br/>(publish fail, checksum mismatch, queue lag)"]
    O3["Runbooks<br/>(rollback, retry, cleanup)"]
  end

  J1["jobs/ingest/*.json"]
  J2["jobs/asr/*.json"]
  J3["jobs/photogrammetry/*.json"]
  J4["jobs/publish/*.json"]
  P1["analytics/*.parquet summaries"]
  R1["gurus/{guru}/registry.json"]
  M1[".../manifest.json"]

  J1 --> O1
  J2 --> O1
  J3 --> O1
  J4 --> O1
  P1 --> O1

  O1 --> O2

  O2 --> O3

  O3 -->|Rollback| R1
  O3 -->|Rebuild/Retry| J2
  O3 -->|Invalidate| M1

```

## Storage & control files (at-a-glance)

```mermaid
classDiagram
  class RegistryJSON {
    guru_id : string
    versions : string[]
    current : string
    updated_at : datetime
  }
  class ManifestJSON {
    guru_id : string
    version : string
    phoneme_map_uri : string
    assets : list
    created_at : datetime
    min_app_version : string
  }
  class PhonemeMapJSON {
    alphabet : string
    rules : list
    fallback : string
  }
  class JobsJSON {
    job_id : string
    stage : string
    status : string
    guru_id : string
    video_id : string
    version : string
    inputs : map
    outputs : map
    error : string
    queued_at : datetime
    started_at : datetime
    finished_at : datetime
  }

  RegistryJSON <.. ManifestJSON : current → version
  ManifestJSON o-- PhonemeMapJSON : references
  JobsJSON ..> ManifestJSON : publish step
```

## Release flow (blue/green)

```mermaid
flowchart LR
  A[Build vNext under _staging] --> B[Generate manifest.json]
  B --> C[Verify checksums and completeness]
  C -->|Copy to final version/| D[Update registry.json `current`]
  D --> E{Canary OK?}
  E -- Yes --> F[All devices pull new manifest]
  E -- No --> G[Rollback to previous version]
```

