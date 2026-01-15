FROM maven:3.9.9-eclipse-temurin-11 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:11-jre
ARG TARGETARCH
WORKDIR /app

# Install system dependencies
# Added libboost-all-dev to the ARM64/AARCH64 branch to fix the build error
RUN apt-get update && \
    if [ "$TARGETARCH" = "arm64" ] || [ "$TARGETARCH" = "aarch64" ]; then \
        apt-get install -y \
            git \
            cmake \
            pkg-config \
            build-essential \
            libopenblas-dev \
            ffmpeg \
            wget \
            unzip \
            colmap \
            pocketsphinx \
            pocketsphinx-en-us \
            qtbase5-dev \
            qtbase5-dev-tools \
            libqt5svg5-dev \
            libboost-all-dev; \
    else \
        apt-get install -y \
            git \
            cmake \
            pkg-config \
            build-essential \
            libopenblas-dev \
            ffmpeg \
            wget \
            unzip \
            colmap \
            pocketsphinx \
            pocketsphinx-en-us; \
    fi && \
    rm -rf /var/lib/apt/lists/*

# Install Vosk based on architecture
RUN if [ "$TARGETARCH" = "arm64" ] || [ "$TARGETARCH" = "aarch64" ]; then \
        wget -q https://github.com/alphacep/vosk-api/releases/download/v0.3.45/vosk-linux-aarch64-0.3.45.zip && \
        unzip -q vosk-linux-aarch64-0.3.45.zip && \
        cp vosk-linux-aarch64-0.3.45/libvosk.so /usr/local/lib/ && \
        rm -rf vosk-linux-aarch64-0.3.45*; \
    else \
        wget -q https://github.com/alphacep/vosk-api/releases/download/v0.3.45/vosk-linux-x86_64-0.3.45.zip && \
        unzip -q vosk-linux-x86_64-0.3.45.zip && \
        cp vosk-linux-x86_64-0.3.45/libvosk.so /usr/local/lib/ && \
        rm -rf vosk-linux-x86_64-0.3.45*; \
    fi && \
    ldconfig

# Install shared runtime libraries for Qt and other dependencies
# Also install Mesa for software OpenGL rendering (required for colmap dense reconstruction)
# Xvfb provides a virtual framebuffer for headless OpenGL applications
RUN apt-get update && apt-get install -y --no-install-recommends \
    libc6 \
    libqt5core5a \
    libqt5gui5 \
    libqt5widgets5 \
    libqt5svg5 \
    libqt5xml5 \
    libxkbcommon-x11-0 \
    libxcb-xinerama0 \
    libxcb1 \
    libx11-6 \
    libx11-xcb1 \
    libxcb-xkb1 \
    libxkbcommon0 \
    ca-certificates \
    libgl1 \
    libglx0 \
    libgl1-mesa-dri \
    mesa-utils \
    xvfb \
    && rm -rf /var/lib/apt/lists/*

# Rhubarb Installation
RUN apt-get update && apt-get install -y file  # Ensure the 'file' command exists

RUN if [ "$TARGETARCH" = "arm64" ] || [ "$TARGETARCH" = "aarch64" ]; then \
        echo "Building rhubarb from source for ARM64..." && \
        git clone --recursive --branch v1.13.0 --depth 1 https://github.com/DanielSWolf/rhubarb-lip-sync.git /tmp/rhubarb-src && \
        rm -rf /tmp/rhubarb-src/extras/EsotericSoftwareSpine && \
        sed -i '/EsotericSoftwareSpine/d' /tmp/rhubarb-src/CMakeLists.txt && \
        mkdir -p /tmp/rhubarb-src/build && cd /tmp/rhubarb-src/build && \
        cmake .. -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=/usr/local -DBUILD_TESTS=OFF && \
        make -j$(nproc) && \
        make install && \
        # Handle the binary location if it installed to /usr/local/rhubarb instead of bin
        if [ -f "/usr/local/rhubarb" ]; then \
            install -m 0755 /usr/local/rhubarb /usr/local/bin/rhubarb; \
        fi && \
        # Handle the 'res' directory
        if [ -d "/usr/local/res" ]; then \
            mkdir -p /usr/local/bin/res && cp -r /usr/local/res/* /usr/local/bin/res/; \
        elif [ -d "/tmp/rhubarb-src/res" ]; then \
            mkdir -p /usr/local/bin/res && cp -r /tmp/rhubarb-src/res/* /usr/local/bin/res/; \
        fi && \
        cd / && rm -rf /tmp/rhubarb-src && \
        apt-get purge -y qtbase5-dev qtbase5-dev-tools libqt5svg5-dev libboost-all-dev && \
        apt-get autoremove -y; \
    else \
        echo "Downloading pre-built rhubarb for x86_64..." && \
        wget -q https://github.com/DanielSWolf/rhubarb-lip-sync/releases/download/v1.13.0/Rhubarb-Lip-Sync-1.13.0-Linux.zip && \
        unzip -q Rhubarb-Lip-Sync-1.13.0-Linux.zip && \
        install -m 0755 Rhubarb-Lip-Sync-1.13.0-Linux/rhubarb /usr/local/bin/rhubarb && \
        if [ -d "Rhubarb-Lip-Sync-1.13.0-Linux/res" ]; then \
            mkdir -p /usr/local/bin/res && cp -r Rhubarb-Lip-Sync-1.13.0-Linux/res/* /usr/local/bin/res/; \
        fi && \
        rm -rf Rhubarb* ; \
    fi && \
    # VERIFICATION (Safely inside the block)
    echo "Verifying rhubarb installation..." && \
    ls -la /usr/local/bin/rhubarb && \
    file /usr/local/bin/rhubarb && \
    rm -rf /var/lib/apt/lists/*

# Final setup - create symlinks to ensure rhubarb is found in common PATH locations
RUN ln -sf /usr/local/bin/rhubarb /usr/bin/rhubarb && \
    ln -sf /usr/local/bin/rhubarb /bin/rhubarb && \
    mkdir -p /root/.cache/vosk/models/vosk-model-en-us-0.22/ && \
    echo "Final verification of rhubarb..." && \
    echo "Checking if rhubarb exists:" && \
    ls -la /usr/local/bin/rhubarb /usr/bin/rhubarb /bin/rhubarb && \
    echo "Checking rhubarb file type:" && \
    file /usr/local/bin/rhubarb && \
    echo "Checking rhubarb dependencies:" && \
    ldd /usr/local/bin/rhubarb 2>&1 | head -20 || echo "ldd completed" && \
    echo "Testing rhubarb execution:" && \
    /usr/local/bin/rhubarb --version 2>&1 || echo "Note: rhubarb --version may fail but binary should still work"

COPY --from=build /app/target/vortex-service-1.0.0-SNAPSHOT.jar app.jar
RUN mkdir -p /tmp/vortex-assets

EXPOSE 8080 9090
ENTRYPOINT ["java", \
  "-Xms256m", \
  "-Xmx2g", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=200", \
  "-XX:+UseStringDeduplication", \
  "-XX:MaxMetaspaceSize=512m", \
  "-Djava.library.path=/usr/local/lib", \
  "-jar", \
  "app.jar", \
  "--spring.profiles.active=prod"]