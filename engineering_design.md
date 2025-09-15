# Vortex Guru — Engineering Design Document

**Author**: Peter Goshomi  
**Date**: Sept 2025  
**Version**: Draft v1.1 (Markdown + Mermaid)

---

## 1. Overview
Vortex Guru is a volumetric avatar display system that projects 3D content using a rotating LED matrix (HUB75 panels) synchronized with a motor. The system consists of:

- **Device Node**: Raspberry Pi (C++) controlling motor, LED panels, and rendering.  
- **Service Layer**: Java/Temporal orchestration (scheduling, health, asset workflow) exposed via REST (OpenAPI) and gRPC to devices.

The design separates **real-time rendering** from **durable orchestration**.

---

## 2. Goals
- Stable real-time playback (30–60 FPS) with low jitter.  
- Remote orchestration (start/stop, asset selection, scheduling).  
- Durable workflows (retries, recovery, audit).  
- Extensible APIs for admin/clients (OpenAPI), device link via gRPC.  

---

## 3. Architecture Overview

```mermaid
flowchart TB
  subgraph Service ["Service Layer (Java, Temporal, OpenAPI)"]
    API["API Gateway (REST/OpenAPI)"]
    WF["Temporal Workflows (Schedule/Health/Assets)"]
    REG["Device Registry (Postgres)"]
    CM["Content Store (S3-compatible)"]
    OBS["Observability (Prometheus/Grafana/Loki)"]
  end

  subgraph Device ["Device Node (Raspberry Pi, C++)"]
    MC["Motor Control (PWM + Encoder)"]
    LED["HUB75 LED Driver (rpi-rgb-led-matrix)"]
    REN["Voxel Renderer (Voxelize→Slice→FrameBuf)"]
    DAPI["gRPC Device API"]
    LC["Local Cache"]
  end

  API <--> WF
  WF --> REG
  WF --> CM
  OBS -.-> API
  OBS -.-> WF

  API <-. "gRPC/Protobuf" .-> DAPI
  DAPI --> REN --> LED
  MC --- REN
  LC --- REN

```

---

## 4. Device Design (C++)

### 4.1 Hardware
- Raspberry Pi 4 Model B (4–8GB RAM)  
- HUB75 LED Matrix Panels (64×64, chained)  
- BLDC motor + encoder, hall-effect zero-point sensor  
- Power: 5V/12V rails, UPS module for safe shutdown  

### 4.2 Software Modules
1. **Motor Control** — PWM via `pigpio`, closed-loop RPM with encoder feedback  
2. **LED Driver** — `rpi-rgb-led-matrix`, double-buffered frames  
3. **Voxel Renderer** — voxelize → slice → frame buffer (Eigen/OpenGL ES optional)  
4. **Networking Layer** — gRPC server (`SetRPM`, `StartShow`, `StopShow`, `UploadAsset`, `GetHealth`)  
5. **System Daemon** — `systemd` unit, watchdog, **local asset cache** for offline mode  

### 4.3 Device Control State Machine

```mermaid
stateDiagram-v2
  [*] --> Idle
  Idle --> Ready : Boot + Zero-Point Calibrated
  Ready --> Playing : StartShow(asset)
  Playing --> Paused : StopShow(pause=true)
  Paused --> Playing : Resume
  Playing --> Fault : RPM Drift / Overtemp / LED Fault
  Fault --> Ready : Auto-Recover or Manual Reset
  Ready --> Idle : Shutdown
```

### 4.4 Data Flow (Device)

```mermaid
sequenceDiagram
  participant Service as Service Workflow
  participant Device as Device gRPC
  participant Cache as Local Cache
  participant Renderer as Voxel Renderer
  participant LED as HUB75 Driver
  participant Motor as Motor Ctrl

  Service->>Device: StartShow(asset_id, target_rpm)
  Device->>Cache: Check(asset_id)
  alt present
    Cache-->>Device: Asset bytes
  else miss
    Device->>Service: FetchAsset(asset_id)
    Service-->>Device: Asset stream (chunks)
    Device->>Cache: Store(asset_id)
  end
  Device->>Motor: SetRPM(target_rpm)
  Device->>Renderer: Prepare(asset_id)
  loop per-frame (16ms)
    Renderer->>LED: PushFrame(buffer)
    Motor-->>Renderer: Encoder tick (phase)
  end
  Device-->>Service: Health{rpm,fps,temp}
```

---

## 5. Service Design (Java)

### 5.1 Components
- **Temporal Workflows**
  - `ScheduleShowWorkflow`: orchestrates show start/stop, timing, retries  
  - `DeviceHealthWorkflow`: periodic health query, alerting on anomalies  
  - `AssetDistributionWorkflow`: upload/replicate, checksum verify  
- **API Gateway (OpenAPI/REST)**
  - `POST /devices/{id}/schedule`, `GET /devices/{id}/health`, `POST /assets`, `GET /shows/history`  
- **Storage**
  - **Postgres** (device registry, show history, asset metadata)  
  - **S3** (asset blobs)  
- **Observability**
  - **Prometheus + Grafana** (metrics), **Loki/ELK** (logs), alerts to Slack/PagerDuty  

### 5.2 Workflow-Orchestration Sequence (Service-centric)

```mermaid
sequenceDiagram
  autonumber
  participant Admin as Admin UI
  participant API as REST API
  participant WF as Temporal Workflow
  participant REG as Device Registry (PG)
  participant S3 as Content Store (S3)
  participant DEV as Device gRPC

  Admin->>API: POST /devices/{id}/schedule {asset, startAt, rpm}
  API->>WF: Start ScheduleShowWorkflow
  WF->>REG: Validate device & firmware
  WF->>S3: Ensure asset availability
  Note over WF: Wait until startAt (durable sleep)
  WF->>DEV: StartShow(asset, rpm) via gRPC
  DEV-->>WF: Ack + Health stream
  WF->>REG: Persist run record
  WF-->>API: 202 Accepted + workflowId
```

---

## 6. Interfaces

### 6.1 Device gRPC (proto excerpt)

```proto
syntax = "proto3";

service VortexDevice {
  rpc SetRPM(RpmRequest) returns (Ack);
  rpc StartShow(ShowRequest) returns (Ack);
  rpc StopShow(StopRequest) returns (Ack);
  rpc UploadAsset(stream AssetChunk) returns (Ack);
  rpc GetHealth(HealthRequest) returns (HealthResponse);
}

message RpmRequest { int32 rpm = 1; }
message ShowRequest { string asset_id = 1; int32 target_rpm = 2; }
message StopRequest { bool pause = 1; }
message AssetChunk { bytes data = 1; string asset_id = 2; int32 seq = 3; }
message HealthRequest {}
message HealthResponse { int32 rpm = 1; int32 fps = 2; float temp = 3; string fw = 4; }
message Ack { bool ok = 1; string msg = 2; }
```

### 6.2 Service REST (OpenAPI excerpt)

```yaml
paths:
  /devices/{id}/schedule:
    post:
      summary: Schedule a show on device
      parameters:
        - name: id
          in: path
          required: true
          schema: { type: string }
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ScheduleShowRequest'
      responses:
        '202':
          description: Accepted (workflow started)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ScheduleAck'

components:
  schemas:
    ScheduleShowRequest:
      type: object
      required: [assetId, startAt, rpm]
      properties:
        assetId: { type: string }
        startAt: { type: string, format: date-time }
        rpm: { type: integer, minimum: 60, maximum: 2400 }
    ScheduleAck:
      type: object
      properties:
        workflowId: { type: string }
        deviceId: { type: string }
        state: { type: string, enum: [scheduled, running, completed, failed] }
```

---

## 7. Non-Functional Requirements
- **Real-time**: ≤16 ms/frame (60 FPS), ≤1 ms encode/phase jitter  
- **Durability**: workflow retries with exponential backoff, idempotent device commands  
- **Security**: TLS everywhere, mTLS for device links; rotate device certs  
- **Scalability**: 1K+ devices, sharded workflow task queues  
- **Extensibility**: versioned gRPC/REST (`v1`), backward-compatible schemas  
- **Maintainability**: separate repos; CI/CD with hardware-in-the-loop tests on the device branch  

---

## 8. Risks & Mitigations
- **Motor sync drift** → encoder feedback + phase-locked render; auto-recalibrate on drift  
- **Overtemp/PSU brownouts** → thermal sensors plus throttling; gracefully degrade; UPS SIGPWR handling  
- **Network loss** → device caches last N shows, reconnection backoff, local schedule  
- **Frame latency spikes** → double-buffering, precomputed slices, CPU affinity for render thread  
- **Asset corruption** → checksums (SHA-256), length-prefix chunking, resume uploads  

---

## 9. Deployment & Environments

```mermaid
graph LR
  subgraph Cloud
    APIGW["API Gateway (REST)"] --- WFS["Temporal Server"]
    DB[("Postgres")]
    OBJ[("S3")]
    Obs["Prometheus / Grafana / Loki"]
    APIGW --- WFS
    WFS --- DB
    WFS --- OBJ
    Obs --- APIGW
    Obs --- WFS
  end

  subgraph Edge
    Pi["Device Node (Raspberry Pi)"]
    Pi --- Enc["Encoder / Hall"]
    Pi --- HUB75["LED Panels (HUB75)"]
    Pi --- Motor["BLDC Motor"]
  end

  APIGW == "gRPC/TLS" ==> Pi

```

**Envs**: `dev` (simulated device), `staging` (few real devices), `prod` (fleet)  
**Config**: all via env vars/Consul; device uses read-only root + overlay for cache  

---

## 10. Implementation Notes

### 10.1 Device (C++)
- **Build**: CMake, cross-compile toolchain for Pi  
- **Libs**: `pigpio`, `rpi-rgb-led-matrix`, `protobuf`/`gRPC`, `Eigen`, optional OpenCV/GL ES  
- **Runtime**: `systemd` unit with watchdog and restart policy  
- **Testing**: GoogleTest + HIL tests that replay encoder traces; perf counters for FPS/jitter  

### 10.2 Service (Java)
- **Runtime**: Java 21 LTS  
- **Frameworks**: Spring Boot or Quarkus; Temporal Java SDK; OpenAPI Generator  
- **Storage**: Postgres (Flyway migrations), S3 (MinIO for dev)  
- **Observability**: Micrometer + Prometheus; structured JSON logs; tracing via OTEL  

---

## 11. Roadmap
- **Phase 1 (MVP)**: Single device; gRPC control; manual asset upload; heartbeat  
- **Phase 2**: Temporal scheduling; multi-device fan-out; health dashboards  
- **Phase 3**: CDN-like asset distribution; analytics; admin RBAC  
- **Phase 4**: Interactive avatars; gesture input; on-device ML pose fitting  

---

## 12. Appendices

### 12.1 Example `systemd` service (device)
```ini
[Unit]
Description=Vortex Device Service
After=network-online.target

[Service]
ExecStart=/usr/local/bin/vortex_device --config /etc/vortex/device.yaml
Restart=always
WatchdogSec=10
Environment=GPRC_TLS_CERT=/etc/vortex/certs/device.crt

[Install]
WantedBy=multi-user.target
```

### 12.2 Health Telemetry (JSON)
```json
{
  "deviceId": "pi-001",
  "timestamp": "2025-09-10T20:30:00Z",
  "rpm": 1200,
  "fps": 60,
  "tempC": 58.2,
  "voltage": 11.9,
  "errors": []
}
```
